package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.services.UserService;
import com.github.kaivu.vertxweb.web.RouterHelper;
import com.github.kaivu.vertxweb.web.validation.ValidationResult;
import com.github.kaivu.vertxweb.web.validation.Validator;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.Getter;

@Singleton
public class UserRouter {

    @Getter
    private final Router router;

    private final UserService userService;
    private final RouterHelper routerHelper;

    @Inject
    public UserRouter(Vertx vertx, UserService userService, RouterHelper routerHelper) {
        this.router = Router.router(vertx);
        this.userService = userService;
        this.routerHelper = routerHelper;
        setupRoutes();
    }

    private void setupRoutes() {
        // Add BodyHandler to parse request bodies
        router.route().handler(BodyHandler.create());

        // API routes using clean async pattern
        router.get().handler(ctx -> RouterHelper.handleAsync(ctx, this::getAllUsers));
        router.get("/:id").handler(ctx -> RouterHelper.handleAsync(ctx, this::getUserById));
        router.post().handler(ctx -> RouterHelper.handleAsync(ctx, this::createUser));
        router.put("/:id").handler(ctx -> RouterHelper.handleAsync(ctx, this::updateUser));
        router.delete("/:id").handler(ctx -> RouterHelper.handleAsync(ctx, this::deleteUser));
    }

    private Uni<Void> getAllUsers(RoutingContext ctx) {
        return userService
                .getAllUsers()
                .onItem()
                .invoke(users -> RouterHelper.sendJsonResponse(ctx, AppConstants.Status.OK, users))
                .replaceWithVoid();
    }

    /**
     * Gets user by ID with improved error handling using RouterHelper.
     * This method demonstrates clean path parameter validation and response handling.
     */
    private Uni<Void> getUserById(RoutingContext ctx) {
        // Validate path parameter using RouterHelper
        String userId = routerHelper.validatePathParam(ctx, "id");

        return userService
                .getUserById(userId)
                .onItem()
                .invoke(user -> RouterHelper.sendJsonResponse(ctx, AppConstants.Status.OK, user))
                .replaceWithVoid();
    }

    /**
     * Creates a new user with improved error handling using RouterHelper.
     * This method demonstrates the clean, reactive approach with RouterHelper utility.
     */
    private Uni<Void> createUser(RoutingContext ctx) {
        // Validate request body using RouterHelper
        JsonObject body = routerHelper.validateRequestBody(ctx);

        // Apply validation rules using RouterHelper
        ValidationResult validation = Validator.Users.CREATE.validate(body);
        routerHelper.handleValidationErrors(validation);

        return userService
                .createUser(body)
                .onItem()
                .invoke(newUser -> {
                    JsonObject response = new JsonObject()
                            .put("message", "User created successfully")
                            .put("user", newUser);
                    RouterHelper.sendJsonResponse(ctx, AppConstants.Status.CREATED, response);
                })
                .replaceWithVoid();
    }

    private Uni<Void> updateUser(RoutingContext ctx) {
        // Validate path parameter using RouterHelper
        String userId = routerHelper.validatePathParam(ctx, "id");

        // Validate request body using RouterHelper
        JsonObject body = routerHelper.validateRequestBody(ctx);

        // Apply validation rules using RouterHelper
        ValidationResult validation = Validator.Users.UPDATE.validate(body);
        routerHelper.handleValidationErrors(validation);

        return userService
                .updateUser(userId, body)
                .onItem()
                .invoke(updatedUser -> {
                    JsonObject response = new JsonObject()
                            .put("message", "User updated successfully")
                            .put("user", updatedUser);
                    RouterHelper.sendJsonResponse(ctx, AppConstants.Status.OK, response);
                })
                .replaceWithVoid();
    }

    private Uni<Void> deleteUser(RoutingContext ctx) {
        // Validate path parameter using RouterHelper
        String userId = routerHelper.validatePathParam(ctx, "id");

        return userService
                .deleteUser(userId)
                .onItem()
                .invoke(result -> RouterHelper.sendJsonResponse(ctx, AppConstants.Status.OK, result))
                .replaceWithVoid();
    }
}
