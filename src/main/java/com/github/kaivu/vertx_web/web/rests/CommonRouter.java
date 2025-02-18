package com.github.kaivu.vertx_web.web.rests;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CommonRouter {
  private static final String CONTENT_TYPE = "application/json";
  private static final int HTTP_OK = 200;
  private static final int HTTP_INTERNAL_ERROR = 500;
  private static final Set<String> ALLOWED_HEADERS = new HashSet<>(Arrays.asList(
    HttpHeaders.CONTENT_TYPE.toString(),
    HttpHeaders.AUTHORIZATION.toString()
  ));
  private static final Set<HttpMethod> ALLOWED_METHODS = new HashSet<>(Arrays.asList(
    HttpMethod.GET,
    HttpMethod.POST,
    HttpMethod.PUT,
    HttpMethod.DELETE,
    HttpMethod.OPTIONS
  ));

  private final Router router;

  public CommonRouter(final Vertx vertx) {
    this.router = Router.router(vertx);
    setupCors();
    initializeRoutes();
  }

  private void setupCors() {
    router.route().handler(CorsHandler.create()
      .addOrigin("*")
      .allowedHeaders(ALLOWED_HEADERS)
      .allowedMethods(ALLOWED_METHODS));
  }

  private void initializeRoutes() {
    router.get("/")
      .handler(this::publicHandler)
      .failureHandler(this::handleError);
  }

  private void publicHandler(RoutingContext ctx) {
    JsonObject response = new JsonObject()
      .put("message", "This is a public endpoint, no authentication required.")
      .put("status", "success")
      .put("timestamp", System.currentTimeMillis());

    sendJsonResponse(ctx, HTTP_OK, response);
  }

  private void handleError(RoutingContext ctx) {
    Throwable failure = ctx.failure();
    String errorMessage = failure != null ? failure.getMessage() : "Unknown error";

    JsonObject errorResponse = new JsonObject()
      .put("error", errorMessage)
      .put("status", "error")
      .put("timestamp", System.currentTimeMillis());

    sendJsonResponse(ctx, HTTP_INTERNAL_ERROR, errorResponse);
  }

  private void sendJsonResponse(RoutingContext ctx, int statusCode, JsonObject response) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE)
      .putHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
      .end(response.encode());
  }

  public Router getRouter() {
    return router;
  }
}
