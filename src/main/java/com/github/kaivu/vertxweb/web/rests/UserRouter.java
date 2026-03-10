package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.ContextKeys;
import com.github.kaivu.vertxweb.constants.HttpStatusCodes;
import com.github.kaivu.vertxweb.constants.PathConstants;
import com.github.kaivu.vertxweb.context.ContextAwareVertxWrapper;
import com.github.kaivu.vertxweb.context.CorrelationContext;
import com.github.kaivu.vertxweb.services.UserService;
import com.github.kaivu.vertxweb.web.AppRouter;
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
public class UserRouter implements AppRouter {

    @Getter
    private final Router router;

    private final UserService userService;
    private final RouterHelper routerHelper;
    private final ApplicationConfig appConfig;

    @Inject
    public UserRouter(Vertx vertx, ApplicationConfig appConfig, UserService userService, RouterHelper routerHelper) {
        this.router = Router.router(vertx);
        this.appConfig = appConfig;
        this.userService = userService;
        this.routerHelper = routerHelper;
        setupRoutes(appConfig);
    }

    @Override
    public String getMountPath() {
        return appConfig.server().apiPrefix() + PathConstants.USERS_ROOT + PathConstants.ANY_ROOT;
    }

    private void setupRoutes(ApplicationConfig appConfig) {
        router.route()
                .handler(BodyHandler.create().setBodyLimit(appConfig.server().maxBodySizeBytes()));

        router.get().handler(ctx -> RouterHelper.handleAsync(ctx, this::getAllUsers));
        router.get("/:id").handler(ctx -> RouterHelper.handleAsync(ctx, this::getUserById));
        router.post().handler(ctx -> RouterHelper.handleAsync(ctx, this::createUser));
        router.put("/:id").handler(ctx -> RouterHelper.handleAsync(ctx, this::updateUser));
        router.delete("/:id").handler(ctx -> RouterHelper.handleAsync(ctx, this::deleteUser));
    }

    private Uni<Void> getAllUsers(RoutingContext ctx) {
        return userService
                .getAllUsersWithContext(extractCorrelation(ctx))
                .onItem()
                .invoke(users -> RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, users))
                .replaceWithVoid();
    }

    private Uni<Void> getUserById(RoutingContext ctx) {
        String userId = routerHelper.validatePathParam(ctx, "id");
        return userService
                .getUserByIdWithContext(userId, extractCorrelation(ctx))
                .onItem()
                .invoke(user -> RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, user))
                .replaceWithVoid();
    }

    private Uni<Void> createUser(RoutingContext ctx) {
        JsonObject body = routerHelper.validateRequestBody(ctx);
        ValidationResult validation = Validator.Users.CREATE.validate(body);
        routerHelper.handleValidationErrors(validation);

        return userService
                .createUserWithContext(body, extractCorrelation(ctx))
                .onItem()
                .invoke(newUser -> {
                    JsonObject response = new JsonObject()
                            .put("message", "User created successfully")
                            .put("user", newUser);
                    RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.CREATED, response);
                })
                .replaceWithVoid();
    }

    private Uni<Void> updateUser(RoutingContext ctx) {
        String userId = routerHelper.validatePathParam(ctx, "id");
        JsonObject body = routerHelper.validateRequestBody(ctx);
        ValidationResult validation = Validator.Users.UPDATE.validate(body);
        routerHelper.handleValidationErrors(validation);

        return userService
                .updateUserWithContext(userId, body, extractCorrelation(ctx))
                .onItem()
                .invoke(updatedUser -> {
                    JsonObject response = new JsonObject()
                            .put("message", "User updated successfully")
                            .put("user", updatedUser);
                    RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, response);
                })
                .replaceWithVoid();
    }

    private Uni<Void> deleteUser(RoutingContext ctx) {
        String userId = routerHelper.validatePathParam(ctx, "id");
        return userService
                .deleteUserWithContext(userId, extractCorrelation(ctx))
                .onItem()
                .invoke(result -> RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, result))
                .replaceWithVoid();
    }

    /** Extracts the CorrelationContext seeded by RouterHelper.handleAsync. */
    private static CorrelationContext extractCorrelation(RoutingContext ctx) {
        ContextAwareVertxWrapper wrapper = ctx.get(ContextKeys.ROUTING_CONTEXT_WRAPPER);
        return wrapper != null ? wrapper.getCorrelationContext() : null;
    }
}
