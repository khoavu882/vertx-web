package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.services.UserService;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
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
        // Add BodyHandler to parse request bodies
        router.route().handler(BodyHandler.create());

        // API routes (relative to subrouter mount point)
        router.get().handler(this::getAllUsers);
        router.get("/:id").handler(this::getUserById);
        router.post().handler(this::createUser);
        router.put("/:id").handler(this::updateUser);
        router.delete("/:id").handler(this::deleteUser);
    }

    private void getAllUsers(RoutingContext ctx) {
        userService
                .getAllUsers()
                .subscribe()
                .with(users -> sendResponse(ctx, AppConstants.Status.OK, users), ctx::fail);
    }

    private void getUserById(RoutingContext ctx) {
        String userId = validatePathParam(ctx, "id");
        userService
                .getUserById(userId)
                .subscribe()
                .with(user -> sendResponse(ctx, AppConstants.Status.OK, user), ctx::fail);
    }

    private void createUser(RoutingContext ctx) {
        JsonObject body = validateRequestBody(ctx);
        if (body == null) return;
        if (isValidUserData(body)) {
            throw new ServiceException(AppConstants.Messages.INVALID_USER_DATA, AppConstants.Status.BAD_REQUEST);
        }
        userService
                .createUser(body)
                .subscribe()
                .with(
                        newUser -> sendResponse(
                                ctx,
                                AppConstants.Status.CREATED,
                                new JsonObject()
                                        .put("message", "User created successfully")
                                        .put("user", newUser)),
                        ctx::fail);
    }

    private void updateUser(RoutingContext ctx) {
        String userId = validatePathParam(ctx, "id");
        JsonObject body = validateRequestBody(ctx);
        if (body == null) return;
        if (isValidUserData(body)) {
            throw new ServiceException(AppConstants.Messages.INVALID_USER_DATA, AppConstants.Status.BAD_REQUEST);
        }
        userService
                .updateUser(userId, body)
                .subscribe()
                .with(
                        updatedUser -> sendResponse(
                                ctx,
                                AppConstants.Status.OK,
                                new JsonObject()
                                        .put("message", "User updated successfully")
                                        .put("user", updatedUser)),
                        ctx::fail);
    }

    private void deleteUser(RoutingContext ctx) {
        String userId = validatePathParam(ctx, "id");
        userService
                .deleteUser(userId)
                .subscribe()
                .with(
                        msg -> sendResponse(
                                ctx,
                                AppConstants.Status.OK,
                                new JsonObject().put("message", msg).put("id", userId)),
                        ctx::fail);
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

    private void sendResponse(RoutingContext ctx, int statusCode, JsonObject response) {
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE + "; " + CHARSET)
                .end(response.encode());
    }
}
