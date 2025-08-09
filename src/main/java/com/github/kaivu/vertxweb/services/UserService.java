package com.github.kaivu.vertxweb.services;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.context.ContextAwareVertxWrapper;
import com.github.kaivu.vertxweb.patterns.CircuitBreakerRegistry;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Khoa Vu.
 * Mail: kai.vu.dev@gmail.com
 * Date: 9/12/24
 * Time: 10:25 AM
 */
@Singleton
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final Vertx vertx;
    private final ApplicationConfig appConfig;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Inject
    public UserService(Vertx vertx, ApplicationConfig appConfig, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.vertx = vertx;
        this.appConfig = appConfig;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    public Uni<JsonObject> getAllUsers() {
        return getAllUsersWithContext(null);
    }

    public Uni<JsonObject> getAllUsersWithContext(RoutingContext ctx) {
        ContextAwareVertxWrapper wrapper = ctx != null ? (ContextAwareVertxWrapper) ctx.get("contextWrapper") : null;

        if (wrapper != null) {
            wrapper.logEvent("service_operation_start", "operation", "getAllUsers");
        }

        log.info("Fetching all users...");

        Uni<JsonObject> result =
                circuitBreakerRegistry.getDatabaseCircuitBreaker().execute(() -> performGetAllUsers());

        if (wrapper != null) {
            wrapper.logEvent("service_operation_completed", "operation", "getAllUsers");
        }

        return result;
    }

    private Uni<JsonObject> performGetAllUsers() {
        return Uni.createFrom()
                .item(0)
                .onItem()
                .delayIt()
                .by(Duration.ofMillis(appConfig.service().baseDelayMs()
                        + ThreadLocalRandom.current()
                                .nextInt(appConfig.service().maxDelayVarianceMs())))
                .onItem()
                .transform(ignored -> {
                    JsonArray users = new JsonArray()
                            .add(new JsonObject()
                                    .put("id", 1)
                                    .put("name", "John Doe")
                                    .put("email", "john@example.com"))
                            .add(new JsonObject()
                                    .put("id", 2)
                                    .put("name", "Jane Smith")
                                    .put("email", "jane@example.com"))
                            .add(new JsonObject()
                                    .put("id", 3)
                                    .put("name", "Bob Johnson")
                                    .put("email", "bob@example.com"));

                    return new JsonObject()
                            .put("users", users)
                            .put("total", users.size())
                            .put("timestamp", System.currentTimeMillis());
                })
                .onFailure()
                .transform(throwable -> {
                    log.error("Error fetching users", throwable);
                    return new ServiceException("Failed to fetch users", AppConstants.Status.INTERNAL_SERVER_ERROR);
                });
    }

    public Uni<JsonObject> getUserById(String userId) {
        return getUserByIdWithContext(userId, null);
    }

    public Uni<JsonObject> getUserByIdWithContext(String userId, RoutingContext ctx) {
        if (userId == null || userId.isBlank()) {
            return Uni.createFrom()
                    .failure(new ServiceException("User ID must not be empty", AppConstants.Status.BAD_REQUEST));
        }

        ContextAwareVertxWrapper wrapper = ctx != null ? (ContextAwareVertxWrapper) ctx.get("contextWrapper") : null;

        if (wrapper != null) {
            wrapper.logEvent("service_operation_start", "operation", "getUserById", "userId", userId);
        }

        log.info("Fetching user by ID: {}", userId);

        Uni<JsonObject> result =
                circuitBreakerRegistry.getDatabaseCircuitBreaker().execute(() -> performGetUserById(userId));

        if (wrapper != null) {
            wrapper.logEvent("service_operation_completed", "operation", "getUserById", "userId", userId);
        }

        return result;
    }

    private Uni<JsonObject> performGetUserById(String userId) {
        return Uni.createFrom()
                .item(userId)
                .onItem()
                .delayIt()
                .by(Duration.ofMillis(appConfig.service().userFetchBaseDelayMs()
                        + ThreadLocalRandom.current()
                                .nextInt(appConfig.service().userFetchMaxVarianceMs())))
                .onItem()
                .transform(id -> {
                    // Simulate database lookup
                    return switch (id) {
                        case "1" -> new JsonObject()
                                .put("id", 1)
                                .put("name", "John Doe")
                                .put("email", "john@example.com")
                                .put("active", true)
                                .put("createdAt", "2024-01-01T10:00:00Z");
                        case "2" -> new JsonObject()
                                .put("id", 2)
                                .put("name", "Jane Smith")
                                .put("email", "jane@example.com")
                                .put("active", true)
                                .put("createdAt", "2024-01-15T14:30:00Z");
                        case "3" -> new JsonObject()
                                .put("id", 3)
                                .put("name", "Bob Johnson")
                                .put("email", "bob@example.com")
                                .put("active", false)
                                .put("createdAt", "2024-02-01T09:15:00Z");
                        default -> throw new ServiceException("User not found", AppConstants.Status.NOT_FOUND);
                    };
                })
                .onFailure(ServiceException.class)
                .recoverWithUni(failure -> Uni.createFrom().failure(failure))
                .onFailure()
                .transform(throwable -> {
                    log.error("Error fetching user: {}", userId, throwable);
                    return new ServiceException("Failed to fetch user", AppConstants.Status.INTERNAL_SERVER_ERROR);
                });
    }

    public Uni<JsonObject> createUser(JsonObject user) {
        return createUserWithContext(user, null);
    }

    public Uni<JsonObject> createUserWithContext(JsonObject user, RoutingContext ctx) {
        if (user == null || user.isEmpty()) {
            return Uni.createFrom()
                    .failure(new ServiceException("User data must not be empty", AppConstants.Status.BAD_REQUEST));
        }

        String name = user.getString("name");
        String email = user.getString("email");

        if (name == null || name.isBlank()) {
            return Uni.createFrom()
                    .failure(new ServiceException("User name is required", AppConstants.Status.BAD_REQUEST));
        }
        if (email == null || email.isBlank()) {
            return Uni.createFrom()
                    .failure(new ServiceException("User email is required", AppConstants.Status.BAD_REQUEST));
        }

        ContextAwareVertxWrapper wrapper = ctx != null ? (ContextAwareVertxWrapper) ctx.get("contextWrapper") : null;

        if (wrapper != null) {
            wrapper.logEvent("service_operation_start", "operation", "createUser", "userName", name);
        }

        log.info("Creating new user: {}", name);

        Uni<JsonObject> result =
                circuitBreakerRegistry.getDatabaseCircuitBreaker().execute(() -> performCreateUser(user));

        if (wrapper != null) {
            wrapper.logEvent("service_operation_completed", "operation", "createUser", "userName", name);
        }

        return result;
    }

    private Uni<JsonObject> performCreateUser(JsonObject user) {
        return Uni.createFrom()
                .item(user)
                .onItem()
                .delayIt()
                .by(Duration.ofMillis(appConfig.service().createBaseDelayMs()
                        + ThreadLocalRandom.current()
                                .nextInt(appConfig.service().createMaxVarianceMs())))
                .onItem()
                .transform(userData -> {
                    // Simulate database insert with generated ID
                    int newId = ThreadLocalRandom.current()
                            .nextInt(
                                    appConfig.service().minIdRange(),
                                    appConfig.service().maxIdRange());
                    return new JsonObject()
                            .put("id", newId)
                            .put("name", userData.getString("name"))
                            .put("email", userData.getString("email"))
                            .put("active", true)
                            .put("createdAt", java.time.Instant.now().toString());
                })
                .onFailure()
                .transform(throwable -> {
                    log.error("Error creating user", throwable);
                    return new ServiceException("Failed to create user", AppConstants.Status.INTERNAL_SERVER_ERROR);
                });
    }

    public Uni<JsonObject> updateUser(String userId, JsonObject user) {
        return updateUserWithContext(userId, user, null);
    }

    public Uni<JsonObject> updateUserWithContext(String userId, JsonObject user, RoutingContext ctx) {
        if (userId == null || userId.isBlank()) {
            return Uni.createFrom()
                    .failure(new ServiceException("User ID must not be empty", AppConstants.Status.BAD_REQUEST));
        }
        if (user == null || user.isEmpty()) {
            return Uni.createFrom()
                    .failure(new ServiceException("User data must not be empty", AppConstants.Status.BAD_REQUEST));
        }

        ContextAwareVertxWrapper wrapper = ctx != null ? (ContextAwareVertxWrapper) ctx.get("contextWrapper") : null;

        if (wrapper != null) {
            wrapper.logEvent("service_operation_start", "operation", "updateUser", "userId", userId);
        }

        log.info("Updating user: {}", userId);

        return circuitBreakerRegistry.getDatabaseCircuitBreaker().execute(() -> getUserByIdWithContext(userId, ctx)
                .onItem()
                .delayIt()
                .by(Duration.ofMillis(appConfig.service().updateBaseDelayMs()
                        + ThreadLocalRandom.current()
                                .nextInt(appConfig.service().updateMaxVarianceMs())))
                .onItem()
                .transform(existingUser -> {
                    // Merge updates with existing data
                    JsonObject updatedUser = existingUser.copy();

                    if (user.containsKey("name")) {
                        updatedUser.put("name", user.getString("name"));
                    }
                    if (user.containsKey("email")) {
                        updatedUser.put("email", user.getString("email"));
                    }
                    if (user.containsKey("active")) {
                        updatedUser.put("active", user.getBoolean("active"));
                    }

                    updatedUser.put("updatedAt", java.time.Instant.now().toString());
                    return updatedUser;
                })
                .onFailure()
                .transform(throwable -> {
                    if (throwable instanceof ServiceException) {
                        return throwable;
                    }
                    log.error("Error updating user: {}", userId, throwable);
                    return new ServiceException("Failed to update user", AppConstants.Status.INTERNAL_SERVER_ERROR);
                }));
    }

    public Uni<JsonObject> deleteUser(String userId) {
        return deleteUserWithContext(userId, null);
    }

    public Uni<JsonObject> deleteUserWithContext(String userId, RoutingContext ctx) {
        if (userId == null || userId.isBlank()) {
            return Uni.createFrom()
                    .failure(new ServiceException("User ID must not be empty", AppConstants.Status.BAD_REQUEST));
        }

        ContextAwareVertxWrapper wrapper = ctx != null ? (ContextAwareVertxWrapper) ctx.get("contextWrapper") : null;

        if (wrapper != null) {
            wrapper.logEvent("service_operation_start", "operation", "deleteUser", "userId", userId);
        }

        log.info("Deleting user: {}", userId);

        return circuitBreakerRegistry.getDatabaseCircuitBreaker().execute(() -> getUserByIdWithContext(userId, ctx)
                .onItem()
                .delayIt()
                .by(Duration.ofMillis(appConfig.service().deleteBaseDelayMs()
                        + ThreadLocalRandom.current()
                                .nextInt(appConfig.service().deleteMaxVarianceMs())))
                .onItem()
                .transform(existingUser -> {
                    // Simulate soft delete
                    JsonObject result = new JsonObject()
                            .put("id", userId)
                            .put("message", "User deleted successfully")
                            .put("deletedAt", java.time.Instant.now().toString());

                    if (wrapper != null) {
                        wrapper.logEvent("service_operation_completed", "operation", "deleteUser", "userId", userId);
                    }

                    return result;
                })
                .onFailure()
                .transform(throwable -> {
                    if (throwable instanceof ServiceException) {
                        return throwable;
                    }
                    log.error("Error deleting user: {}", userId, throwable);
                    return new ServiceException("Failed to delete user", AppConstants.Status.INTERNAL_SERVER_ERROR);
                }));
    }
}
