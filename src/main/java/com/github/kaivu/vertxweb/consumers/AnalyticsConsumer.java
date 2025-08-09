package com.github.kaivu.vertxweb.consumers;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.context.ContextAwareVertxWrapper;
import com.github.kaivu.vertxweb.context.CorrelationContext;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnalyticsConsumer implements EventBusConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsConsumer.class);
    private static final String REQUEST_ID_KEY = "requestId";
    private static final Random RANDOM = new Random();
    private final Vertx vertx;
    private final ApplicationConfig appConfig;

    @Inject
    public AnalyticsConsumer(Vertx vertx, ApplicationConfig appConfig) {
        this.vertx = vertx;
        this.appConfig = appConfig;
    }

    @Override
    public String getEventAddress() {
        return appConfig.analytics().eventAddress();
    }

    @Override
    public void registerConsumer(EventBus eventBus) {
        eventBus.<JsonObject>consumer(getEventAddress(), this::handle);
    }

    public void handle(Message<JsonObject> message) {
        ContextAwareVertxWrapper wrapper = null;
        try {
            JsonObject requestData = message.body();

            // Extract correlation context from message
            wrapper = ContextAwareVertxWrapper.fromEventBusMessage(vertx, requestData);
            CorrelationContext context = wrapper.getCorrelationContext();

            // Make final references for lambda
            final ContextAwareVertxWrapper finalWrapper = wrapper;
            final CorrelationContext finalContext = context;

            // Set up logging context for structured logging
            context.setupLoggingContext();

            wrapper.logEvent("analytics_report_start", "operation", "analytics-report");

            // Validate request data with context
            validateAnalyticsRequest(requestData, context);

            // Use wrapper's executeBlocking for context-aware processing
            wrapper.executeBlocking(
                            () -> generateAnalyticsReport(requestData, finalContext),
                            appConfig.analytics().executionTimeoutMs(),
                            "Analytics report generation failed")
                    .onSuccess(report -> {
                        finalWrapper.logEvent(
                                "analytics_report_completed",
                                "duration_ms",
                                finalContext.getProcessingDurationMs(),
                                "correlation_id",
                                finalContext.getCorrelationId());
                        message.reply(report.encode());
                    })
                    .onFailure(error -> {
                        finalWrapper.logEvent(
                                "analytics_report_failed",
                                "error",
                                error.getMessage(),
                                "correlation_id",
                                finalContext.getCorrelationId());

                        if (error instanceof ServiceException) {
                            ServiceException se = (ServiceException) error;
                            message.fail(se.getStatusCode(), se.getMessage());
                        } else {
                            message.fail(
                                    AppConstants.Status.INTERNAL_SERVER_ERROR,
                                    "Internal server error: " + error.getMessage());
                        }
                    });

        } catch (ServiceException e) {
            log.error("Service error generating analytics report: {}", e.getMessage());
            message.fail(e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error generating analytics report", e);
            message.fail(AppConstants.Status.INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        } finally {
            // Always clean up logging context
            if (wrapper != null) {
                wrapper.getCorrelationContext().clearLoggingContext();
            }
        }
    }

    private void validateAnalyticsRequest(JsonObject requestData, CorrelationContext context) {
        if (requestData == null) {
            throw new ServiceException("Request data cannot be null", AppConstants.Status.BAD_REQUEST);
        }

        // Validate correlation context exists
        if (context == null || context.getCorrelationId() == null) {
            throw new ServiceException("Correlation context is required", AppConstants.Status.BAD_REQUEST);
        }

        String reportType = requestData.getString("reportType");
        if (reportType == null || !reportType.equals("analytics")) {
            throw new ServiceException("Invalid report type. Expected 'analytics'", AppConstants.Status.BAD_REQUEST);
        }

        Long timestamp = requestData.getLong("timestamp");
        if (timestamp == null || timestamp <= 0) {
            throw new ServiceException("Valid timestamp is required", AppConstants.Status.BAD_REQUEST);
        }

        // Check if request is not too old (e.g., more than 1 hour)
        long currentTime = System.currentTimeMillis();
        if (currentTime - timestamp > appConfig.analytics().requestExpirationMs()) { // Request expiration check
            throw new ServiceException("Request has expired. Please generate a new request", AppConstants.Status.GONE);
        }

        log.debug("Analytics request validated for correlation: {}", context.getCorrelationId());
    }

    private JsonObject generateAnalyticsReport(JsonObject requestData, CorrelationContext context) {
        try {
            log.info("Generating analytics report with correlation: {}", context.getCorrelationId());

            // Simulate database queries with correlation context
            Thread.sleep(appConfig.analytics().databaseQueryDelayMs()); // Simulate database query
            log.debug("Database queries completed for correlation: {}", context.getCorrelationId());

            // Simulate file I/O operations
            Thread.sleep(appConfig.analytics().fileProcessingDelayMs()); // Simulate file processing
            log.debug("File processing completed for correlation: {}", context.getCorrelationId());

            // Generate mock analytics data
            int totalProducts = RANDOM.nextInt(appConfig.analytics().maxProducts())
                    + appConfig.analytics().minProducts();
            double totalRevenue = RANDOM.nextDouble() * appConfig.analytics().maxRevenue();
            double averageOrderValue =
                    RANDOM.nextDouble() * appConfig.analytics().maxOrderValue()
                            + appConfig.analytics().minOrderValue();

            return new JsonObject()
                    .put("correlationId", context.getCorrelationId())
                    .put("requestId", context.getRequestId())
                    .put("reportType", "analytics")
                    .put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .put("processingTimeMs", context.getProcessingDurationMs())
                    .put("totalProducts", totalProducts)
                    .put("totalRevenue", totalRevenue)
                    .put("topCategory", "Electronics")
                    .put("averageOrderValue", averageOrderValue)
                    .put("userId", context.getUserId())
                    .put("tenantId", context.getTenantId())
                    .put("status", "completed");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Report generation was interrupted", AppConstants.Status.SERVICE_UNAVAILABLE);
        }
    }
}
