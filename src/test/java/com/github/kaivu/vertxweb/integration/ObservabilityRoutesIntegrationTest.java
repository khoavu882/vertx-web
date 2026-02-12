package com.github.kaivu.vertxweb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.kaivu.vertxweb.config.AppModule;
import com.github.kaivu.vertxweb.web.routes.RouterConfig;
import com.google.inject.Guice;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class ObservabilityRoutesIntegrationTest {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();

    @Test
    void metricsEndpointShouldBeProtectedWhenExposureIsProtected() throws Exception {
        try (TestServer server = TestServer.start(Map.of(
                "app.security.enable-auth",
                "true",
                "app.observability.metrics.enable",
                "true",
                "app.observability.metrics.exposure",
                "protected"))) {
            HttpResponse<String> response = get(server, "/metrics");

            assertEquals(401, response.statusCode());
            assertTrue(response.body().contains("Unauthorized"));
        }
    }

    @Test
    void metricsEndpointShouldBePublicWhenExposureIsOpen() throws Exception {
        try (TestServer server = TestServer.start(Map.of(
                "app.security.enable-auth",
                "true",
                "app.observability.metrics.enable",
                "true",
                "app.observability.metrics.exposure",
                "open"))) {
            HttpResponse<String> response = get(server, "/metrics");

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"));
            assertFalse(response.body().isBlank());
        }
    }

    @Test
    void healthCanonicalRoutesShouldRemainAvailableWhenLegacyAliasesDisabled() throws Exception {
        try (TestServer server = TestServer.start(Map.of(
                "app.security.enable-auth",
                "true",
                "app.observability.health.enable",
                "true",
                "app.observability.health.legacy-aliases",
                "false",
                "app.observability.health.check-timeout-ms",
                "50"))) {
            HttpResponse<String> overall = get(server, "/health");
            HttpResponse<String> live = get(server, "/health/live");
            HttpResponse<String> ready = get(server, "/health/ready");
            HttpResponse<String> started = get(server, "/health/started");

            assertNotEquals(404, overall.statusCode());
            assertNotEquals(404, live.statusCode());
            assertNotEquals(404, ready.statusCode());
            assertNotEquals(404, started.statusCode());
            assertTrue(overall.body().contains("\"status\""));
            assertTrue(live.body().contains("\"status\""));
            assertTrue(ready.body().contains("\"status\""));
            assertTrue(started.body().contains("\"status\""));
        }
    }

    @Test
    void healthAliasesShouldMapToCanonicalRoutesWhenEnabled() throws Exception {
        try (TestServer server = TestServer.start(Map.of(
                "app.security.enable-auth",
                "true",
                "app.observability.health.enable",
                "true",
                "app.observability.health.legacy-aliases",
                "true",
                "app.observability.health.check-timeout-ms",
                "50"))) {
            HttpResponse<String> live = get(server, "/health/live");
            HttpResponse<String> livenessAlias = get(server, "/health/liveness");
            HttpResponse<String> ready = get(server, "/health/ready");
            HttpResponse<String> readinessAlias = get(server, "/health/readiness");

            assertEquals(live.statusCode(), livenessAlias.statusCode());
            assertEquals(ready.statusCode(), readinessAlias.statusCode());
            assertTrue(livenessAlias.body().contains("\"status\""));
            assertTrue(readinessAlias.body().contains("\"status\""));
        }
    }

    @Test
    void healthAliasesShouldReturnNotFoundWhenDisabled() throws Exception {
        try (TestServer server = TestServer.start(Map.of(
                "app.security.enable-auth",
                "true",
                "app.observability.health.enable",
                "true",
                "app.observability.health.legacy-aliases",
                "false",
                "app.observability.health.check-timeout-ms",
                "50"))) {
            HttpResponse<String> livenessAlias = get(server, "/health/liveness");
            HttpResponse<String> readinessAlias = get(server, "/health/readiness");

            assertEquals(404, livenessAlias.statusCode());
            assertEquals(404, readinessAlias.statusCode());
        }
    }

    private HttpResponse<String> get(TestServer server, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(server.baseUrl() + path))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static final class TestServer implements AutoCloseable {
        private final Vertx vertx;
        private final HttpServer server;
        private final Map<String, String> originalProperties;
        private final Set<String> overriddenKeys;

        private TestServer(
                Vertx vertx, HttpServer server, Map<String, String> originalProperties, Set<String> overriddenKeys) {
            this.vertx = vertx;
            this.server = server;
            this.originalProperties = originalProperties;
            this.overriddenKeys = overriddenKeys;
        }

        static TestServer start(Map<String, String> overrides)
                throws ExecutionException, InterruptedException, TimeoutException {
            Map<String, String> originals = captureAndApply(overrides);
            Vertx vertx = Vertx.vertx();
            try {
                Router router = Guice.createInjector(new AppModule(vertx))
                        .getInstance(RouterConfig.class)
                        .getRouter();
                HttpServer server = vertx.createHttpServer()
                        .requestHandler(router)
                        .listen(0, "127.0.0.1")
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
                return new TestServer(vertx, server, originals, overrides.keySet());
            } catch (Exception e) {
                restoreProperties(originals, overrides.keySet());
                vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
                throw e;
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.actualPort();
        }

        @Override
        public void close() throws Exception {
            try {
                server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            } finally {
                try {
                    vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
                } finally {
                    restoreProperties(originalProperties, overriddenKeys);
                }
            }
        }

        private static Map<String, String> captureAndApply(Map<String, String> overrides) {
            Map<String, String> originals = new java.util.HashMap<>();
            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                String key = entry.getKey();
                originals.put(key, System.getProperty(key));
                System.setProperty(key, entry.getValue());
            }
            return originals;
        }

        private static void restoreProperties(Map<String, String> originals, Set<String> keys) {
            for (String key : keys) {
                String previous = originals.get(key);
                if (previous == null) {
                    System.clearProperty(key);
                } else {
                    System.setProperty(key, previous);
                }
            }
        }
    }
}
