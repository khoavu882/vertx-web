package com.github.kaivu.vertx_web.web.rests;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.handler.BodyHandler;

public class UserRouter {
  private final Router router;
  private static final String CONTENT_TYPE = "application/json";
  private static final String CHARSET = "charset=utf-8";

  public UserRouter(Vertx vertx) {
    this.router = Router.router(vertx);
    setupRoutes();
  }

  private void setupRoutes() {
    // Add BodyHandler to parse request bodies
    router.route().handler(BodyHandler.create());

    // API routes
    router.get("/api/users").handler(this::getAllUsers);
    router.get("/api/users/:id").handler(this::getUserById);
    router.post("/api/users").handler(this::createUser);
    router.put("/api/users/:id").handler(this::updateUser);
    router.delete("/api/users/:id").handler(this::deleteUser);

    // Global error handler
    router.route().failureHandler(this::handleError);
  }

  private void getAllUsers(RoutingContext ctx) {
    try {
      JsonArray users = new JsonArray()
        .add(new JsonObject()
          .put("id", 1)
          .put("name", "John Doe")
          .put("email", "john@example.com"))
        .add(new JsonObject()
          .put("id", 2)
          .put("name", "Jane Doe")
          .put("email", "jane@example.com"));

      sendResponse(ctx, 200, new JsonObject().put("users", users));
    } catch (Exception e) {
      ctx.fail(e);
    }
  }

  private void getUserById(RoutingContext ctx) {
    try {
      String userId = validatePathParam(ctx, "id");
      if (userId == null) return;

      JsonObject user = new JsonObject()
        .put("id", Integer.parseInt(userId))
        .put("name", "John Doe")
        .put("email", "john@example.com");

      sendResponse(ctx, 200, user);
    } catch (NumberFormatException e) {
      sendError(ctx, 400, "Invalid user ID format");
    } catch (Exception e) {
      ctx.fail(e);
    }
  }

  private void createUser(RoutingContext ctx) {
    try {
      JsonObject body = validateRequestBody(ctx);
      if (body == null) return;

      if (!isValidUserData(body)) {
        sendError(ctx, 400, "Invalid user data. Required fields: name, email");
        return;
      }

      JsonObject newUser = new JsonObject()
        .put("id", generateUserId())
        .put("name", body.getString("name"))
        .put("email", body.getString("email"));

      sendResponse(ctx, 201, new JsonObject()
        .put("message", "User created successfully")
        .put("user", newUser));
    } catch (Exception e) {
      ctx.fail(e);
    }
  }

  private void updateUser(RoutingContext ctx) {
    try {
      String userId = validatePathParam(ctx, "id");
      if (userId == null) return;

      JsonObject body = validateRequestBody(ctx);
      if (body == null) return;

      if (!isValidUserData(body)) {
        sendError(ctx, 400, "Invalid user data. Required fields: name, email");
        return;
      }

      JsonObject updatedUser = body.copy()
        .put("id", Integer.parseInt(userId));

      sendResponse(ctx, 200, new JsonObject()
        .put("message", "User updated successfully")
        .put("user", updatedUser));
    } catch (NumberFormatException e) {
      sendError(ctx, 400, "Invalid user ID format");
    } catch (Exception e) {
      ctx.fail(e);
    }
  }

  private void deleteUser(RoutingContext ctx) {
    try {
      String userId = validatePathParam(ctx, "id");
      if (userId == null) return;

      sendResponse(ctx, 200, new JsonObject()
        .put("message", "User deleted successfully")
        .put("id", Integer.parseInt(userId)));
    } catch (NumberFormatException e) {
      sendError(ctx, 400, "Invalid user ID format");
    } catch (Exception e) {
      ctx.fail(e);
    }
  }

  private String validatePathParam(RoutingContext ctx, String param) {
    String value = ctx.pathParam(param);
    if (value == null || value.trim().isEmpty()) {
      sendError(ctx, 400, "Missing required path parameter: " + param);
      return null;
    }
    return value;
  }

  private JsonObject validateRequestBody(RoutingContext ctx) {
    if (!ctx.body().available()) {
      sendError(ctx, 400, "Request body is required");
      return null;
    }
    return ctx.body().asJsonObject();
  }

  private boolean isValidUserData(JsonObject userData) {
    return userData != null &&
      userData.containsKey("name") &&
      userData.containsKey("email") &&
      !userData.getString("name", "").trim().isEmpty() &&
      !userData.getString("email", "").trim().isEmpty();
  }

  private int generateUserId() {
    // In a real application, this would be handled by a database
    return (int) (Math.random() * 10000);
  }

  private void sendResponse(RoutingContext ctx, int statusCode, JsonObject response) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE + "; " + CHARSET)
      .end(response.encode());
  }

  private void sendError(RoutingContext ctx, int statusCode, String message) {
    JsonObject response = new JsonObject()
      .put("error", message)
      .put("status", statusCode);

    sendResponse(ctx, statusCode, response);
  }

  private void handleError(RoutingContext ctx) {
    Throwable error = ctx.failure();
    int statusCode = ctx.statusCode() != -1 ? ctx.statusCode() : 500;

    JsonObject response = new JsonObject()
      .put("error", error != null ? error.getMessage() : "Internal Server Error")
      .put("status", statusCode);

    sendResponse(ctx, statusCode, response);
  }

  public Router getRouter() {
    return this.router;
  }
}
