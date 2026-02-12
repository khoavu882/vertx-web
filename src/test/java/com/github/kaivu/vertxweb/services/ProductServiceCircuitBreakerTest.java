package com.github.kaivu.vertxweb.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.config.ConfigProvider;
import com.github.kaivu.vertxweb.constants.*;
import com.github.kaivu.vertxweb.observability.metrics.NoopMetricsFacade;
import com.github.kaivu.vertxweb.patterns.CircuitBreaker;
import com.github.kaivu.vertxweb.patterns.CircuitBreakerRegistry;
import com.github.kaivu.vertxweb.repositories.ProductRepository;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ProductServiceCircuitBreakerTest {
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(2);

    @Test
    void shouldOpenCircuitAfterConsecutiveServerFailures() throws Exception {
        try (TestFixture fixture = TestFixture.start(new ProductRepository() {
            @Override
            public Uni<JsonObject> findById(String productId) {
                return Uni.createFrom().item(new JsonObject().put("productId", productId));
            }

            @Override
            public Uni<List<JsonObject>> findAll() {
                return Uni.createFrom()
                        .failure(new ServiceException("Repository unavailable", HttpStatusCodes.INTERNAL_SERVER_ERROR));
            }
        })) {
            for (int i = 0; i < 5; i++) {
                ServiceException failure =
                        assertThrows(ServiceException.class, () -> await(fixture.productService.getAllProducts()));
                assertEquals(HttpStatusCodes.INTERNAL_SERVER_ERROR, failure.getStatusCode());
            }

            assertThrows(
                    CircuitBreaker.CircuitBreakerOpenException.class,
                    () -> await(fixture.productService.getAllProducts()));
            assertEquals(
                    CircuitBreaker.State.OPEN,
                    fixture.circuitBreakerRegistry.getDatabaseCircuitBreaker().getState());
        }
    }

    @Test
    void shouldKeepCircuitClosedForClientFailures() throws Exception {
        try (TestFixture fixture = TestFixture.start(new ProductRepository() {
            @Override
            public Uni<JsonObject> findById(String productId) {
                return Uni.createFrom().failure(new ServiceException("Product not found", HttpStatusCodes.NOT_FOUND));
            }

            @Override
            public Uni<List<JsonObject>> findAll() {
                return Uni.createFrom().item(List.of());
            }
        })) {
            for (int i = 0; i < 8; i++) {
                ServiceException failure = assertThrows(
                        ServiceException.class, () -> await(fixture.productService.getProductById("missing")));
                assertEquals(HttpStatusCodes.NOT_FOUND, failure.getStatusCode());
            }

            assertEquals(
                    CircuitBreaker.State.CLOSED,
                    fixture.circuitBreakerRegistry.getDatabaseCircuitBreaker().getState());
        }
    }

    private static <T> T await(Uni<T> uni) {
        return uni.await().atMost(AWAIT_TIMEOUT);
    }

    private static final class TestFixture implements AutoCloseable {
        private final Vertx vertx;
        private final ProductService productService;
        private final CircuitBreakerRegistry circuitBreakerRegistry;

        private TestFixture(Vertx vertx, ProductService productService, CircuitBreakerRegistry circuitBreakerRegistry) {
            this.vertx = vertx;
            this.productService = productService;
            this.circuitBreakerRegistry = circuitBreakerRegistry;
        }

        static TestFixture start(ProductRepository productRepository) {
            Vertx vertx = Vertx.vertx();
            ApplicationConfig appConfig = ConfigProvider.createConfig();
            CircuitBreakerRegistry circuitBreakerRegistry =
                    new CircuitBreakerRegistry(vertx, appConfig, new NoopMetricsFacade());
            ProductService productService =
                    new ProductService(productRepository, vertx, appConfig, circuitBreakerRegistry);
            return new TestFixture(vertx, productService, circuitBreakerRegistry);
        }

        @Override
        public void close() throws Exception {
            vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }
}
