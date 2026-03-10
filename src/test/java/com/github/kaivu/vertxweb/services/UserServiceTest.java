package com.github.kaivu.vertxweb.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.config.ConfigProvider;
import com.github.kaivu.vertxweb.constants.HttpStatusCodes;
import com.github.kaivu.vertxweb.constants.JsonKeys;
import com.github.kaivu.vertxweb.observability.metrics.NoopMetricsFacade;
import com.github.kaivu.vertxweb.patterns.CircuitBreakerRegistry;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class UserServiceTest {
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(2);

    @Test
    void getAllUsersShouldReturnUsersPayload() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            JsonObject result = await(fixture.userService.getAllUsers());

            assertEquals(3, result.getInteger("total"));
            assertEquals(3, result.getJsonArray("users").size());
            assertTrue(result.containsKey(JsonKeys.TIMESTAMP));
        }
    }

    @Test
    void getUserByIdShouldFailWhenIdIsBlank() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            ServiceException failure =
                    assertThrows(ServiceException.class, () -> await(fixture.userService.getUserById(" ")));
            assertEquals(HttpStatusCodes.BAD_REQUEST, failure.getStatusCode());
            assertTrue(failure.getMessage().contains("must not be empty"));
        }
    }

    @Test
    void getUserByIdShouldReturnNotFoundForUnknownId() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            ServiceException failure =
                    assertThrows(ServiceException.class, () -> await(fixture.userService.getUserById("999")));
            assertEquals(HttpStatusCodes.NOT_FOUND, failure.getStatusCode());
            assertEquals("User not found", failure.getMessage());
        }
    }

    @Test
    void createUserShouldFailWhenPayloadOrRequiredFieldsAreMissing() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            ServiceException emptyPayload =
                    assertThrows(ServiceException.class, () -> await(fixture.userService.createUser(new JsonObject())));
            assertEquals(HttpStatusCodes.BAD_REQUEST, emptyPayload.getStatusCode());

            ServiceException missingName = assertThrows(
                    ServiceException.class,
                    () -> await(fixture.userService.createUser(new JsonObject().put("email", "alice@example.com"))));
            assertEquals(HttpStatusCodes.BAD_REQUEST, missingName.getStatusCode());

            ServiceException missingEmail = assertThrows(
                    ServiceException.class,
                    () -> await(fixture.userService.createUser(new JsonObject().put("name", "Alice"))));
            assertEquals(HttpStatusCodes.BAD_REQUEST, missingEmail.getStatusCode());
        }
    }

    @Test
    void createUserShouldReturnExpectedPayload() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            JsonObject result = await(fixture.userService.createUser(
                    new JsonObject().put("name", "Alice").put("email", "alice@example.com")));

            assertEquals("Alice", result.getString("name"));
            assertEquals("alice@example.com", result.getString("email"));
            assertTrue(result.getBoolean("active"));
            assertTrue(result.containsKey("id"));
            assertTrue(result.containsKey("createdAt"));
        }
    }

    @Test
    void updateUserShouldFailForInvalidInputAndUnknownId() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            ServiceException blankId = assertThrows(
                    ServiceException.class,
                    () -> await(fixture.userService.updateUser(" ", new JsonObject().put("name", "A"))));
            assertEquals(HttpStatusCodes.BAD_REQUEST, blankId.getStatusCode());

            ServiceException emptyPayload = assertThrows(
                    ServiceException.class, () -> await(fixture.userService.updateUser("1", new JsonObject())));
            assertEquals(HttpStatusCodes.BAD_REQUEST, emptyPayload.getStatusCode());

            ServiceException unknownUser = assertThrows(
                    ServiceException.class,
                    () -> await(fixture.userService.updateUser("999", new JsonObject().put("name", "A"))));
            assertEquals(HttpStatusCodes.NOT_FOUND, unknownUser.getStatusCode());
        }
    }

    @Test
    void updateUserShouldMergeProvidedFields() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            JsonObject result =
                    await(fixture.userService.updateUser("1", new JsonObject().put("name", "Updated User")));

            assertEquals(1, result.getInteger("id"));
            assertEquals("Updated User", result.getString("name"));
            assertEquals("john@example.com", result.getString("email"));
            assertTrue(result.containsKey("updatedAt"));
        }
    }

    @Test
    void deleteUserShouldFailForInvalidInputAndUnknownId() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            ServiceException blankId =
                    assertThrows(ServiceException.class, () -> await(fixture.userService.deleteUser(" ")));
            assertEquals(HttpStatusCodes.BAD_REQUEST, blankId.getStatusCode());

            ServiceException unknownUser =
                    assertThrows(ServiceException.class, () -> await(fixture.userService.deleteUser("999")));
            assertEquals(HttpStatusCodes.NOT_FOUND, unknownUser.getStatusCode());
        }
    }

    @Test
    void deleteUserShouldReturnDeleteConfirmation() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            JsonObject result = await(fixture.userService.deleteUser("1"));

            assertEquals("1", result.getString("id"));
            assertEquals("User deleted successfully", result.getString("message"));
            assertTrue(result.containsKey("deletedAt"));
        }
    }

    private static <T> T await(Uni<T> uni) {
        return uni.await().atMost(AWAIT_TIMEOUT);
    }

    private static final class TestFixture implements AutoCloseable {
        private final Vertx vertx;
        private final UserService userService;
        private final Map<String, String> originals;
        private final Set<String> overriddenKeys;

        private TestFixture(
                Vertx vertx, UserService userService, Map<String, String> originals, Set<String> overriddenKeys) {
            this.vertx = vertx;
            this.userService = userService;
            this.originals = originals;
            this.overriddenKeys = overriddenKeys;
        }

        static TestFixture start() {
            Map<String, String> overrides = Map.ofEntries(
                    Map.entry("app.service.base-delay-ms", "1"),
                    Map.entry("app.service.max-delay-variance-ms", "1"),
                    Map.entry("app.service.user-fetch-base-delay-ms", "1"),
                    Map.entry("app.service.user-fetch-max-variance-ms", "1"),
                    Map.entry("app.service.create-base-delay-ms", "1"),
                    Map.entry("app.service.create-max-variance-ms", "1"),
                    Map.entry("app.service.update-base-delay-ms", "1"),
                    Map.entry("app.service.update-max-variance-ms", "1"),
                    Map.entry("app.service.delete-base-delay-ms", "1"),
                    Map.entry("app.service.delete-max-variance-ms", "1"),
                    Map.entry("app.service.circuit-breaker-timeout-ms", "1000"),
                    Map.entry("app.service.circuit-breaker-failure-threshold", "5"),
                    Map.entry("app.service.circuit-breaker-success-threshold", "3"),
                    Map.entry("app.service.circuit-breaker-reset-timeout-ms", "60000"));
            Map<String, String> originals = captureAndApply(overrides);

            Vertx vertx = Vertx.vertx();
            ApplicationConfig appConfig = ConfigProvider.createConfig();
            CircuitBreakerRegistry circuitBreakerRegistry =
                    new CircuitBreakerRegistry(vertx, appConfig, new NoopMetricsFacade());
            UserService userService = new UserService(appConfig, circuitBreakerRegistry);
            return new TestFixture(vertx, userService, originals, overrides.keySet());
        }

        @Override
        public void close() throws Exception {
            try {
                vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            } finally {
                restoreProperties(originals, overriddenKeys);
            }
        }

        private static Map<String, String> captureAndApply(Map<String, String> overrides) {
            Map<String, String> originals = new HashMap<>();
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
