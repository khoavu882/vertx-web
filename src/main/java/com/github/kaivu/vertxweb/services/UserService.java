package com.github.kaivu.vertxweb.services;

import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Khoa Vu.
 * Mail: khoavd12@fpt.com
 * Date: 9/12/24
 * Time: 10:25 AM
 */
@Singleton
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final Vertx vertx;

    @Inject
    public UserService(Vertx vertx) {
        this.vertx = vertx;
    }

    public Uni<JsonObject> getAllUsers() {
        log.info("Fetching all users...");

        return Uni.createFrom()
                .item(0)
                .onItem()
                .delayIt()
                .by(Duration.ofMillis(100 + ThreadLocalRandom.current().nextInt(200)))
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
                    return new ServiceException("Failed to fetch users", 500);
                });
    }

    public Uni<JsonObject> getUserById(String userId) {
        if (userId == null || userId.isBlank()) {
            return Uni.createFrom().failure(new ServiceException("User ID must not be empty", 400));
        }

        log.info("Fetching user by ID: {}", userId);

        return Uni.createFrom()
                .item(userId)
                .onItem()
                .delayIt()
                .by(Duration.ofMillis(50 + ThreadLocalRandom.current().nextInt(100)))
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
                        default -> throw new ServiceException("User not found", 404);
                    };
                })
                .onFailure(ServiceException.class)
                .recoverWithUni(failure -> Uni.createFrom().failure(failure))
                .onFailure()
                .transform(throwable -> {
                    log.error("Error fetching user: {}", userId, throwable);
                    return new ServiceException("Failed to fetch user", 500);
                });
    }

    public Uni<JsonObject> createUser(JsonObject user) {
        if (user == null || user.isEmpty()) {
            return Uni.createFrom().failure(new ServiceException("User data must not be empty", 400));
        }

        String name = user.getString("name");
        String email = user.getString("email");

        if (name == null || name.isBlank()) {
            return Uni.createFrom().failure(new ServiceException("User name is required", 400));
        }
        if (email == null || email.isBlank()) {
            return Uni.createFrom().failure(new ServiceException("User email is required", 400));
        }

        log.info("Creating new user: {}", name);

        return Uni.createFrom()
                .item(user)
                .onItem()
                .delayIt()
                .by(Duration.ofMillis(200 + ThreadLocalRandom.current().nextInt(300)))
                .onItem()
                .transform(userData -> {
                    // Simulate database insert with generated ID
                    int newId = ThreadLocalRandom.current().nextInt(1000, 9999);
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
                    return new ServiceException("Failed to create user", 500);
                });
    }

    public Uni<JsonObject> updateUser(String userId, JsonObject user) {
        if (userId == null || userId.isBlank()) {
            return Uni.createFrom().failure(new ServiceException("User ID must not be empty", 400));
        }
        if (user == null || user.isEmpty()) {
            return Uni.createFrom().failure(new ServiceException("User data must not be empty", 400));
        }

        log.info("Updating user: {}", userId);

        return getUserById(userId)
                .onItem()
                .delayIt()
                .by(Duration.ofMillis(150 + ThreadLocalRandom.current().nextInt(200)))
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
                    return new ServiceException("Failed to update user", 500);
                });
    }

    public Uni<JsonObject> deleteUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return Uni.createFrom().failure(new ServiceException("User ID must not be empty", 400));
        }

        log.info("Deleting user: {}", userId);

        return getUserById(userId)
                .onItem()
                .delayIt()
                .by(Duration.ofMillis(100 + ThreadLocalRandom.current().nextInt(150)))
                .onItem()
                .transform(existingUser -> {
                    // Simulate soft delete
                    return new JsonObject()
                            .put("id", userId)
                            .put("message", "User deleted successfully")
                            .put("deletedAt", java.time.Instant.now().toString());
                })
                .onFailure()
                .transform(throwable -> {
                    if (throwable instanceof ServiceException) {
                        return throwable;
                    }
                    log.error("Error deleting user: {}", userId, throwable);
                    return new ServiceException("Failed to delete user", 500);
                });
    }
}
