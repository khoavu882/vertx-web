package com.github.kaivu.vertxweb.consumers;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.HttpStatusCodes;
import com.github.kaivu.vertxweb.constants.JsonKeys;
import com.github.kaivu.vertxweb.constants.OutcomeConstants;
import com.github.kaivu.vertxweb.constants.ProductConstants;
import com.github.kaivu.vertxweb.context.ContextAwareVertxWrapper;
import com.github.kaivu.vertxweb.context.CorrelationContext;
import com.github.kaivu.vertxweb.observability.tracing.TracingService;
import com.github.kaivu.vertxweb.patterns.CircuitBreaker;
import com.github.kaivu.vertxweb.patterns.CircuitBreakerRegistry;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import io.opentelemetry.api.trace.Span;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnalyticsConsumer implements EventBusConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsConsumer.class);
    private static final Random RANDOM = new Random();
    private final Vertx vertx;
    private final ApplicationConfig appConfig;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final TracingService tracingService;

    @Inject
    public AnalyticsConsumer(
            Vertx vertx,
            ApplicationConfig appConfig,
            CircuitBreakerRegistry circuitBreakerRegistry,
            TracingService tracingService) {
        this.vertx = vertx;
        this.appConfig = appConfig;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.tracingService = tracingService;
    }

    @Override
    public String getEventAddress() {
        return appConfig.analytics().eventAddress();
    }

    @Override
    public MessageConsumer<JsonObject> registerConsumer(EventBus eventBus) {
        return eventBus.<JsonObject>consumer(getEventAddress(), this::handle);
    }

    public void handle(Message<JsonObject> message) {
        ContextAwareVertxWrapper wrapper = null;
        Span consumerSpan = Span.getInvalid();
        try {
            JsonObject requestData = message.body();
            consumerSpan = tracingService.startEventBusConsumerSpan(getEventAddress(), requestData);

            // Extract correlation context from message
            wrapper = ContextAwareVertxWrapper.fromEventBusMessage(vertx, requestData);
            CorrelationContext context = wrapper.getCorrelationContext();
            tracingService.enrichCorrelationContext(context, consumerSpan);

            // Make final references for lambda
            final ContextAwareVertxWrapper finalWrapper = wrapper;
            final CorrelationContext finalContext = context;
            final Span finalConsumerSpan = consumerSpan;

            wrapper.logEvent(
                    "analytics_report_start", JsonKeys.OPERATION, ProductConstants.LOG_OPERATION_ANALYTICS_REPORT);

            // Validate request data with context
            validateAnalyticsRequest(requestData, context);

            // Use circuit breaker for resilient processing.
            // This handler already runs on the WorkerVerticle thread pool — direct blocking call
            // is correct here; no extra executeBlocking dispatch needed.
            Uni<JsonObject> analyticsUni = circuitBreakerRegistry
                    .getAnalyticsCircuitBreaker()
                    .execute(() -> Uni.createFrom()
                            .<JsonObject>item(() -> generateAnalyticsReport(finalContext))
                            .onFailure()
                            .transform(e -> e instanceof ServiceException
                                    ? e
                                    : new ServiceException(
                                            "Analytics report generation failed",
                                            HttpStatusCodes.INTERNAL_SERVER_ERROR)));
            analyticsUni
                    .subscribe()
                    .with(
                            report -> {
                                finalWrapper.logEvent(
                                        "analytics_report_completed",
                                        "duration_ms",
                                        finalContext.getProcessingDurationMs(),
                                        "correlation_id",
                                        finalContext.getCorrelationId());
                                tracingService.endSpan(finalConsumerSpan, null);
                                message.reply((report).encode());
                            },
                            error -> {
                                finalWrapper.logEvent(
                                        "analytics_report_failed",
                                        "error",
                                        error.getMessage(),
                                        "correlation_id",
                                        finalContext.getCorrelationId());
                                tracingService.endSpan(finalConsumerSpan, error);

                                ServiceException serviceError = normalizeFailure(error);
                                message.fail(serviceError.getStatusCode(), serviceError.getMessage());
                            });

        } catch (ServiceException e) {
            log.error("Service error generating analytics report: {}", e.getMessage());
            tracingService.endSpan(consumerSpan, e);
            message.fail(e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error generating analytics report", e);
            tracingService.endSpan(consumerSpan, e);
            message.fail(HttpStatusCodes.INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        } finally {
            // Always clean up logging context
            if (wrapper != null) {
                wrapper.getCorrelationContext().clearLoggingContext();
            }
        }
    }

    private void validateAnalyticsRequest(JsonObject requestData, CorrelationContext context) {
        if (requestData == null) {
            throw new ServiceException("Request data cannot be null", HttpStatusCodes.BAD_REQUEST);
        }

        // Validate correlation context exists
        if (context == null || context.getCorrelationId() == null) {
            throw new ServiceException("Correlation context is required", HttpStatusCodes.BAD_REQUEST);
        }

        String reportType = requestData.getString(ProductConstants.KEY_REPORT_TYPE);
        if (reportType == null || !reportType.equals(ProductConstants.VALUE_REPORT_TYPE_ANALYTICS)) {
            throw new ServiceException(
                    "Invalid report type. Expected '" + ProductConstants.VALUE_REPORT_TYPE_ANALYTICS + "'",
                    HttpStatusCodes.BAD_REQUEST);
        }

        Long timestamp = requestData.getLong(JsonKeys.TIMESTAMP);
        if (timestamp == null || timestamp <= 0) {
            throw new ServiceException("Valid timestamp is required", HttpStatusCodes.BAD_REQUEST);
        }

        // Check if request is not too old (e.g., more than 1 hour)
        long currentTime = System.currentTimeMillis();
        if (currentTime - timestamp > appConfig.analytics().requestExpirationMs()) { // Request expiration check
            throw new ServiceException("Request has expired. Please generate a new request", HttpStatusCodes.GONE);
        }

        log.debug("Analytics request validated for correlation: {}", context.getCorrelationId());
    }

    private JsonObject generateAnalyticsReport(CorrelationContext context) {
        try {
            log.info("Generating analytics report with correlation: {}", context.getCorrelationId());

            // Simulate database queries with correlation context (non-blocking)
            log.debug("Database queries completed for correlation: {}", context.getCorrelationId());

            // Simulate file I/O operations (non-blocking)
            log.debug("File processing completed for correlation: {}", context.getCorrelationId());

            // Generate mock analytics data
            int totalProducts = RANDOM.nextInt(appConfig.analytics().maxProducts())
                    + appConfig.analytics().minProducts();
            double totalRevenue = RANDOM.nextDouble() * appConfig.analytics().maxRevenue();
            double averageOrderValue =
                    RANDOM.nextDouble() * appConfig.analytics().maxOrderValue()
                            + appConfig.analytics().minOrderValue();

            return new JsonObject()
                    .put(JsonKeys.CORRELATION_ID, context.getCorrelationId())
                    .put("requestId", context.getRequestId())
                    .put(ProductConstants.KEY_REPORT_TYPE, ProductConstants.VALUE_REPORT_TYPE_ANALYTICS)
                    .put(JsonKeys.GENERATED_AT, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .put("processingTimeMs", context.getProcessingDurationMs())
                    .put("totalProducts", totalProducts)
                    .put("totalRevenue", totalRevenue)
                    .put("topCategory", "Electronics")
                    .put("averageOrderValue", averageOrderValue)
                    .put("userId", context.getUserId())
                    .put("tenantId", context.getTenantId())
                    .put(JsonKeys.STATUS, OutcomeConstants.COMPLETED);

        } catch (Exception e) {
            throw new ServiceException(
                    "Report generation failed: " + e.getMessage(), HttpStatusCodes.SERVICE_UNAVAILABLE);
        }
    }

    private ServiceException normalizeFailure(Throwable error) {
        if (error instanceof ServiceException serviceException) {
            return serviceException;
        }

        Throwable rootCause = unwrapRootCause(error);
        if (rootCause instanceof CircuitBreaker.CircuitBreakerOpenException) {
            return new ServiceException("Service temporarily unavailable", HttpStatusCodes.SERVICE_UNAVAILABLE);
        }
        if (rootCause instanceof CircuitBreaker.CircuitBreakerTimeoutException) {
            return new ServiceException("Upstream dependency timed out", HttpStatusCodes.GATEWAY_TIMEOUT);
        }

        String message = error != null && error.getMessage() != null ? error.getMessage() : "Unexpected internal error";
        return new ServiceException("Internal server error: " + message, HttpStatusCodes.INTERNAL_SERVER_ERROR);
    }

    private Throwable unwrapRootCause(Throwable failure) {
        Throwable current = failure;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current != null ? current : failure;
    }
}
