package com.github.kaivu.vertxweb;

import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import java.util.function.Function;
import java.util.function.Supplier;

public class VertxWrapper {
    private final Vertx vertx;
    private final RoutingContext routingContext;
    private final Context context;

    public VertxWrapper(Vertx vertx, RoutingContext routingContext, Context context) {
        this.vertx = vertx;
        this.routingContext = routingContext;
        this.context = context;
    }

    public Vertx vertx() {
        return vertx;
    }

    public VertxWrapper of(Vertx vertx, RoutingContext routingContext) {
        return new VertxWrapper(vertx, routingContext, vertx.getOrCreateContext());
    }

    public RoutingContext routingContext() {
        return routingContext;
    }

    public Context context() {
        return context;
    }

    public <T> Future<T> executeBlocking(
            Supplier<T> blockingCode, Function<Exception, ServiceException> exceptionMapper) {
        return vertx.executeBlocking(() -> {
            try {
                return blockingCode.get();
            } catch (Exception e) {
                throw exceptionMapper.apply(e);
            }
        });
    }

    public <T> Future<T> executeBlocking(Supplier<T> blockingCode, int errorStatusCode, String errorMessage) {
        return executeBlocking(
                blockingCode,
                e -> new ServiceException(errorMessage != null ? errorMessage : e.getMessage(), errorStatusCode));
    }

    public <T> Future<T> executeBlocking(Supplier<T> blockingCode, int errorStatusCode) {
        return executeBlocking(blockingCode, errorStatusCode, null);
    }

    public <T> Future<T> executeBlocking(Supplier<T> blockingCode) {
        return executeBlocking(blockingCode, AppConstants.Status.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    public <T> Uni<T> executeBlockingUni(
            Supplier<T> blockingCode, Function<Exception, ServiceException> exceptionMapper) {
        return Uni.createFrom().completionStage(() -> executeBlocking(blockingCode, exceptionMapper)
                .toCompletionStage());
    }

    public <T> Uni<T> executeBlockingUni(Supplier<T> blockingCode, int errorStatusCode, String errorMessage) {
        return Uni.createFrom().completionStage(() -> executeBlocking(blockingCode, errorStatusCode, errorMessage)
                .toCompletionStage());
    }

    public <T> Uni<T> executeBlockingUni(Supplier<T> blockingCode, int errorStatusCode) {
        return Uni.createFrom().completionStage(() -> executeBlocking(blockingCode, errorStatusCode)
                .toCompletionStage());
    }

    public <T> Uni<T> executeBlockingUni(Supplier<T> blockingCode) {
        return Uni.createFrom()
                .completionStage(() -> executeBlocking(blockingCode).toCompletionStage());
    }
}
