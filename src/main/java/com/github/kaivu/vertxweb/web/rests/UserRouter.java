package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.services.UserService;
import com.github.kaivu.vertxweb.web.ResponseHelper;
import com.github.kaivu.vertxweb.web.RouterHelper;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.Router;
import io.vertx.mutiny.ext.web.RoutingContext;
import io.vertx.mutiny.ext.web.handler.BodyHandler;
import lombok.Getter;

@Singleton
public class UserRouter {

    private static final String CONTENT_TYPE = AppConstants.Http.CONTENT_TYPE_JSON;
    private static final String CHARSET = AppConstants.Http.CHARSET_UTF8;

    @Getter
    private final Router router;

    private final UserService userService;

    @Inject
    public UserRouter(Vertx vertx, UserService userService) {
        this.router = Router.router(vertx);
        this.userService = userService;
        setupRoutes();
    }

    private void setupRoutes() {
        router.route().handler(BodyHandler.create());
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
                .transformToUni(users -> ResponseHelper.ok(ctx, users))
                .onFailure()
                .recoverWithUni(t -> ResponseHelper.internalError(ctx, "Failed to fetch users"));
    }

    private Uni<Void> getUserById(RoutingContext ctx) {
        String userId = validatePathParam(ctx, "id");
        return userService
                .getUserById(userId)
                .onItem()
                .transformToUni(user -> {
                    if (user == null) {
                        return ResponseHelper.notFound(ctx, "User not found");
                    }
                    return ResponseHelper.ok(ctx, user);
                })
                .onFailure()
                .recoverWithUni(t -> ResponseHelper.internalError(ctx, "Failed to fetch user"));
    }

    private Uni<Void> createUser(RoutingContext ctx) {
        JsonObject body = validateRequestBody(ctx);
        if (isValidUserData(body)) {
            return ResponseHelper.badRequest(ctx, AppConstants.Messages.INVALID_USER_DATA);
        }
        return userService
                .createUser(body)
                .onItem()
                .transformToUni(newUser -> ResponseHelper.created(
                        ctx,
                        new JsonObject()
                                .put("message", "User created successfully")
                                .put("user", newUser)))
                .onFailure()
                .recoverWithUni(t -> ResponseHelper.internalError(ctx, "Failed to create user"));
    }

    private Uni<Void> updateUser(RoutingContext ctx) {
        String userId = validatePathParam(ctx, "id");
        JsonObject body = validateRequestBody(ctx);
        if (isValidUserData(body)) {
            return ResponseHelper.badRequest(ctx, AppConstants.Messages.INVALID_USER_DATA);
        }
        return userService
                .updateUser(userId, body)
                .onItem()
                .transformToUni(updatedUser -> ResponseHelper.ok(
                        ctx,
                        new JsonObject()
                                .put("message", "User updated successfully")
                                .put("user", updatedUser)))
                .onFailure()
                .recoverWithUni(t -> ResponseHelper.internalError(ctx, "Failed to update user"));
    }

    private Uni<Void> deleteUser(RoutingContext ctx) {
        String userId = validatePathParam(ctx, "id");
        return userService
                .deleteUser(userId)
                .onItem()
                .transformToUni(msg -> ResponseHelper.ok(
                        ctx, new JsonObject().put("message", msg).put("id", userId)))
                .onFailure()
                .recoverWithUni(t -> ResponseHelper.internalError(ctx, "Failed to delete user"));
    }

    private String validatePathParam(RoutingContext ctx, String param) {
        String value = ctx.pathParam(param);
        if (value == null || value.trim().isEmpty()) {
            throw new ServiceException(
                    AppConstants.Messages.MISSING_PATH_PARAM + param, AppConstants.Status.BAD_REQUEST);
        }
        return value;
    }

    private JsonObject validateRequestBody(RoutingContext ctx) {
        if (!ctx.body().available()) {
            throw new ServiceException(AppConstants.Messages.MISSING_BODY, AppConstants.Status.BAD_REQUEST);
        }
        return ctx.body().asJsonObject();
    }

    private boolean isValidUserData(JsonObject userData) {
        return userData == null
                || !userData.containsKey("name")
                || !userData.containsKey("email")
                || userData.getString("name", "").trim().isEmpty()
                || userData.getString("email", "").trim().isEmpty();
    }
}
