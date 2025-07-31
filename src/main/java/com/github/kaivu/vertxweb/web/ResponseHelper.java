package com.github.kaivu.vertxweb.web;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.Json;
import io.vertx.mutiny.ext.web.RoutingContext;

public class ResponseHelper {

    public static Uni<Void> ok(RoutingContext ctx, Object data) {
        return ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(Json.encode(data)); // Returns Uni<Void>
    }

    public static Uni<Void> created(RoutingContext ctx, Object data) {
        return ctx.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json")
                .end(Json.encode(data)); // Returns Uni<Void>
    }

    public static Uni<Void> noContent(RoutingContext ctx) {
        return ctx.response().setStatusCode(204).end(); // Returns Uni<Void>
    }

    public static Uni<Void> badRequest(RoutingContext ctx, String message) {
        return ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "text/plain")
                .end(message); // Returns Uni<Void>
    }

    public static Uni<Void> notFound(RoutingContext ctx, String message) {
        return ctx.response()
                .setStatusCode(404)
                .putHeader("Content-Type", "text/plain")
                .end(message); // Returns Uni<Void>
    }

    public static Uni<Void> internalError(RoutingContext ctx, String message) {
        return ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "text/plain")
                .end("Internal Server Error: " + message); // Returns Uni<Void>
    }

    public static Uni<Void> unauthorized(RoutingContext ctx, String message) {
        return ctx.response()
                .setStatusCode(401)
                .putHeader("Content-Type", "text/plain")
                .end("Unauthorized: " + message);
    }

    public static Uni<Void> forbidden(RoutingContext ctx, String message) {
        return ctx.response()
                .setStatusCode(403)
                .putHeader("Content-Type", "text/plain")
                .end("Forbidden: " + message);
    }
}
