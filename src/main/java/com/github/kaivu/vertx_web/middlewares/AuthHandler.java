package com.github.kaivu.vertx_web.middlewares;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Created by Khoa Vu.
 * Mail: khoavd12@fpt.com
 * Date: 9/12/24
 * Time: 9:36 AM
 */

public class AuthHandler {
  private static final String VALID_TOKEN = "valid_token";

  public static void authenticateRequest(RoutingContext ctx) {

    if ("/common".startsWith(ctx.request().path())) {
      // Skip authentication for public routes
      ctx.next();
    } else {
      JsonObject body = ctx.body().asJsonObject();
      if (body != null && VALID_TOKEN.equals(body.getString("token"))) {
        ctx.next();
      } else {
        ctx.response().setStatusCode(401).end("Unauthorized: Invalid token");
      }
    }
  }
}

