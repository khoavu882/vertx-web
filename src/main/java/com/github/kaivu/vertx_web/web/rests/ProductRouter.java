package com.github.kaivu.vertx_web.web.rests;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;

public class ProductRouter {
  private static final String CONTENT_TYPE = "application/json";
  private static final int HTTP_OK = 200;
  private static final int HTTP_NOT_FOUND = 404;
  private static final int HTTP_INTERNAL_ERROR = 500;

  private final Router router;

  public ProductRouter(Vertx vertx) {
    this.router = Router.router(vertx);
    setupRoutes();
  }

  private void setupRoutes() {
    router.get("/").handler(this::getAllProducts);
    router.get("/:productId").handler(this::getProductById);

    // Add error handler
    router.errorHandler(500, this::handleError);
  }

  private void getAllProducts(RoutingContext ctx) {
    try {
      JsonObject response = new JsonObject()
        .put("products", new JsonObject()
          .put("productId", 1)
          .put("name", "Widget"))
        .put("timestamp", System.currentTimeMillis());

      sendJsonResponse(ctx, HTTP_OK, response);
    } catch (Exception e) {
      ctx.fail(e);
    }
  }

  private void getProductById(RoutingContext ctx) {
    try {
      String productId = ctx.pathParam("productId");
      if (productId == null || productId.isEmpty()) {
        JsonObject errorResponse = new JsonObject()
          .put("error", "Product ID is required")
          .put("status", "error");
        sendJsonResponse(ctx, HTTP_NOT_FOUND, errorResponse);
        return;
      }

      JsonObject response = new JsonObject()
        .put("productId", productId)
        .put("name", "Widget")
        .put("timestamp", System.currentTimeMillis());

      sendJsonResponse(ctx, HTTP_OK, response);
    } catch (Exception e) {
      ctx.fail(e);
    }
  }

  private void handleError(RoutingContext ctx) {
    Throwable failure = ctx.failure();
    JsonObject errorResponse = new JsonObject()
      .put("error", failure != null ? failure.getMessage() : "Unknown error")
      .put("status", "error")
      .put("timestamp", System.currentTimeMillis());

    sendJsonResponse(ctx, HTTP_INTERNAL_ERROR, errorResponse);
  }

  private void sendJsonResponse(RoutingContext ctx, int statusCode, JsonObject response) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE)
      .end(response.encode());
  }

  public Router getRouter() {
    return this.router;
  }
}
