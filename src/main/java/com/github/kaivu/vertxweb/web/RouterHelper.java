package com.github.kaivu.vertxweb.web;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.ext.web.RoutingContext;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouterHelper {

    private static final Logger log = LoggerFactory.getLogger(RouterHelper.class);

    public static void handleAsync(RoutingContext ctx, Function<RoutingContext, Uni<Void>> handler) {
        handler.apply(ctx)
                .subscribe()
                .with(
                        result -> {
                            log.info(
                                    "Response sent successfully for: {}",
                                    ctx.request().path());
                        },
                        failure -> {
                            log.error("Failed to send response: {}", failure.getMessage());

                            if (!ctx.response().ended()) {
                                ctx.response()
                                        .setStatusCode(500)
                                        .end("Internal Server Error")
                                        .subscribe()
                                        .with(
                                                success -> log.error("Error response sent"),
                                                errorFailure -> log.error(
                                                        "Failed to send error response: {}",
                                                        errorFailure.getMessage()));
                            }
                        });
    }

    // Alternative with better error handling
    public static void handleAsyncWithLogging(RoutingContext ctx, Function<RoutingContext, Uni<Void>> handler) {
        String requestPath = ctx.request().path();
        String method = ctx.request().method().toString();

        handler.apply(ctx)
                .onItem()
                .invoke(() -> log.info("✓ {} {} - Response sent successfully", method, requestPath))
                .onFailure()
                .invoke(failure -> log.error("✗ {} {} - Failed: {}", method, requestPath, failure.getMessage()))
                .subscribe()
                .with(
                        result -> {
                            /* Success already logged */
                        },
                        failure -> {
                            // Final fallback
                            if (!ctx.response().ended()) {
                                sendFallbackErrorResponse(ctx, failure);
                            }
                        });
    }

    private static void sendFallbackErrorResponse(RoutingContext ctx, Throwable failure) {
        ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "text/plain")
                .end("Internal Server Error")
                .subscribe()
                .with(
                        success -> System.out.println("Fallback error response sent"),
                        errorFailure ->
                                log.error("Critical: Failed to send fallback response: {}", errorFailure.getMessage()));
    }
}
