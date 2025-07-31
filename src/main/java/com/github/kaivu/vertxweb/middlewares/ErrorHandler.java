package com.github.kaivu.vertxweb.middlewares;

import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.ext.web.RoutingContext;

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

    public static Uni<Void> handle(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        int statusCode = 500;
        String message = "Internal Server Error";

        if (failure instanceof ServiceException ex) {
            statusCode = ex.getStatusCode();
            message = failure.getMessage();
        } else if (failure != null) {
            message = failure.getMessage();
        }

        return ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                        .put("error", message)
                        .put("status", statusCode)
                        .encode());
    }
}
