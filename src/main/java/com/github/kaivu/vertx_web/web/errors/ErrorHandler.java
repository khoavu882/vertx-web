package com.github.kaivu.vertx_web.web.errors;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Created by Khoa Vu.
 * Mail: khoavd12@fpt.com
 * Date: 9/12/24
 * Time: 9:37 AM
 */
public class ErrorHandler {

    private ErrorHandler() {
        // Private constructor to hide the implicit public one
    }

    public static void globalErrorHandler(RoutingContext ctx) {
        int statusCode = ctx.statusCode() != -1 ? ctx.statusCode() : 500;
        Throwable failure = ctx.failure();

        JsonObject errorResponse = new JsonObject()
                .put("error", failure != null ? failure.getMessage() : "Unknown error")
                .put("status", statusCode);

        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("content-type", "application/json")
                .end(errorResponse.encode());
    }
}
