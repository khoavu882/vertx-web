package com.github.kaivu.vertxweb.context;

import com.github.kaivu.vertxweb.VertxWrapper;
import com.github.kaivu.vertxweb.constants.*;
import com.github.kaivu.vertxweb.observability.tracing.TracingService;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production-grade VertxWrapper with correlation context support.
 * Maintains context across AppVerticle and WorkerVerticle boundaries.
 */
public class ContextAwareVertxWrapper extends VertxWrapper {

    private static final Logger log = LoggerFactory.getLogger(ContextAwareVertxWrapper.class);

    private final CorrelationContext correlationContext;

    public ContextAwareVertxWrapper(
            Vertx vertx, RoutingContext routingContext, Context context, CorrelationContext correlationContext) {
        super(vertx, routingContext, context);
        this.correlationContext = correlationContext;
    }

    public ContextAwareVertxWrapper(Vertx vertx, Context context, CorrelationContext correlationContext) {
        super(vertx, null, context);
        this.correlationContext = correlationContext;
    }

    // Factory methods for different contexts
    public static ContextAwareVertxWrapper fromHttpRequest(Vertx vertx, RoutingContext routingContext) {
        CorrelationContext context = CorrelationContext.create()
                .withSourceIp(routingContext.request().remoteAddress().host())
                .withUserAgent(routingContext.request().getHeader("User-Agent"));

        // Extract correlation headers if present
        String existingCorrelationId = routingContext.request().getHeader(HttpConstants.X_CORRELATION_ID);
        if (existingCorrelationId != null) {
            context.withAttribute(CorrelationContext.CORRELATION_ID, existingCorrelationId);
        }

        String traceId = routingContext.get(TracingService.ROUTING_TRACE_ID_KEY);
        if (traceId != null && !traceId.isBlank()) {
            context.withTraceId(traceId);
        }

        String spanId = routingContext.get(TracingService.ROUTING_SPAN_ID_KEY);
        if (spanId != null && !spanId.isBlank()) {
            context.withSpanId(spanId);
        }

        return new ContextAwareVertxWrapper(vertx, routingContext, vertx.getOrCreateContext(), context);
    }

    public static ContextAwareVertxWrapper fromEventBus(Vertx vertx, CorrelationContext correlationContext) {
        return new ContextAwareVertxWrapper(vertx, vertx.getOrCreateContext(), correlationContext);
    }

    public CorrelationContext getCorrelationContext() {
        return correlationContext;
    }

    /**
     * Execute blocking operation with correlation context maintained.
     */
    @Override
    public <T> Future<T> executeBlocking(
            Supplier<T> blockingCode, Function<Exception, ServiceException> exceptionMapper) {
        // Set up logging context before execution
        correlationContext.setupLoggingContext();

        return super.executeBlocking(
                        () -> {
                            try {
                                // Ensure context is available in blocking thread
                                correlationContext.setupLoggingContext();

                                log.debug("Executing blocking operation with context: {}", correlationContext);
                                long startTime = System.currentTimeMillis();

                                T result = blockingCode.get();

                                long duration = System.currentTimeMillis() - startTime;
                                log.debug(
                                        "Blocking operation completed in {}ms with context: {}",
                                        duration,
                                        correlationContext);

                                return result;
                            } finally {
                                // Always clean up MDC in blocking thread
                                correlationContext.clearLoggingContext();
                            }
                        },
                        exceptionMapper)
                .onComplete(ar -> {
                    // Clean up MDC in original thread
                    correlationContext.clearLoggingContext();
                });
    }

    /**
     * Execute blocking with correlation context and Uni support.
     */
    @Override
    public <T> Uni<T> executeBlockingUni(
            Supplier<T> blockingCode, Function<Exception, ServiceException> exceptionMapper) {
        return Uni.createFrom().completionStage(() -> executeBlocking(blockingCode, exceptionMapper)
                .toCompletionStage());
    }

    /**
     * Create child wrapper for sub-operations (e.g., sending to WorkerVerticle).
     */
    public ContextAwareVertxWrapper createChild(String operation) {
        CorrelationContext childContext = correlationContext.createChild(operation);
        return new ContextAwareVertxWrapper(vertx(), context(), childContext);
    }

    /**
     * Prepare context data for EventBus transmission.
     */
    public void enrichEventBusMessage(io.vertx.core.json.JsonObject message) {
        if (message == null) {
            return;
        }
        message.put(ContextKeys.EVENTBUS_CONTEXT, correlationContext.toJson());

        // Add correlation headers for tracing
        message.put(ContextKeys.EVENTBUS_CORRELATION_ID, correlationContext.getCorrelationId());
        message.put(ContextKeys.EVENTBUS_REQUEST_ID, correlationContext.getRequestId());
        if (correlationContext.getTraceId() != null) {
            message.put(TracingService.EVENTBUS_TRACE_ID, correlationContext.getTraceId());
        }
        if (correlationContext.getSpanId() != null) {
            message.put(TracingService.EVENTBUS_PARENT_SPAN_ID, correlationContext.getSpanId());
        }
    }

    /**
     * Extract context from EventBus message.
     */
    public static ContextAwareVertxWrapper fromEventBusMessage(Vertx vertx, io.vertx.core.json.JsonObject message) {
        io.vertx.core.json.JsonObject contextJson =
                message != null ? message.getJsonObject(ContextKeys.EVENTBUS_CONTEXT) : null;
        CorrelationContext context = CorrelationContext.fromJson(contextJson);
        if (context.getTraceId() == null) {
            context.withTraceId(message != null ? message.getString(TracingService.EVENTBUS_TRACE_ID) : null);
        }
        if (context.getSpanId() == null) {
            context.withSpanId(message != null ? message.getString(TracingService.EVENTBUS_PARENT_SPAN_ID) : null);
        }
        return fromEventBus(vertx, context);
    }

    /**
     * Log structured event with correlation context.
     */
    public void logEvent(String event, Object... args) {
        correlationContext.setupLoggingContext();
        try {
            log.info("Event: {} | Context: {} | Data: {}", event, correlationContext, args);
        } finally {
            correlationContext.clearLoggingContext();
        }
    }
}
