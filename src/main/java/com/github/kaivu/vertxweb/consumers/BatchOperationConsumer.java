package com.github.kaivu.vertxweb.consumers;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.HttpStatusCodes;
import com.github.kaivu.vertxweb.constants.JsonKeys;
import com.github.kaivu.vertxweb.constants.OutcomeConstants;
import com.github.kaivu.vertxweb.constants.ProductConstants;
import com.github.kaivu.vertxweb.observability.tracing.TracingService;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import io.opentelemetry.api.trace.Span;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationConsumer implements EventBusConsumer {

    private static final Logger log = LoggerFactory.getLogger(BatchOperationConsumer.class);
    private static final String OPERATION_KEY = JsonKeys.OPERATION;
    private static final Random RANDOM = new Random();
    private final ApplicationConfig appConfig;
    private final TracingService tracingService;

    @Inject
    public BatchOperationConsumer(ApplicationConfig appConfig, TracingService tracingService) {
        this.appConfig = appConfig;
        this.tracingService = tracingService;
    }

    @Override
    public String getEventAddress() {
        return appConfig.eventBus().batchOperationAddress();
    }

    @Override
    public MessageConsumer<JsonObject> registerConsumer(EventBus eventBus) {
        return eventBus.<JsonObject>consumer(getEventAddress(), this::handle);
    }

    public void handle(Message<JsonObject> message) {
        Span consumerSpan = tracingService.startEventBusConsumerSpan(getEventAddress(), message.body());
        try {
            JsonObject requestData = message.body();
            String operation = requestData.getString(OPERATION_KEY);

            log.info("Starting batch operation: {}", operation);

            // Validate batch request
            validateBatchRequest(requestData);

            // Process batch operation
            JsonObject result = processBatchOperation(requestData);

            log.info("Batch operation completed: {}", operation);
            tracingService.endSpan(consumerSpan, null);
            message.reply(result.encode());

        } catch (ServiceException e) {
            log.error("Service error in batch operation: {}", e.getMessage());
            tracingService.endSpan(consumerSpan, e);
            message.fail(e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in batch operation", e);
            tracingService.endSpan(consumerSpan, e);
            message.fail(HttpStatusCodes.INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }

    private void validateBatchRequest(JsonObject requestData) {
        if (requestData == null) {
            throw new ServiceException("Batch request data cannot be null", HttpStatusCodes.BAD_REQUEST);
        }

        String operation = requestData.getString(OPERATION_KEY);
        if (operation == null || operation.trim().isEmpty()) {
            throw new ServiceException("Operation type is required for batch processing", HttpStatusCodes.BAD_REQUEST);
        }

        // Validate allowed operations
        switch (operation.toLowerCase()) {
            case "insert", "update", "delete", "migrate":
                break;
            default:
                throw new ServiceException(
                        "Unsupported batch operation: " + operation
                                + ". Allowed operations: insert, update, delete, migrate",
                        HttpStatusCodes.BAD_REQUEST);
        }

        // Additional validation based on operation type
        if (operation.equalsIgnoreCase(ProductConstants.VALUE_OPERATION_DELETE)) {
            Boolean confirmDelete = requestData.getBoolean(ProductConstants.KEY_CONFIRM_DELETE);
            if (confirmDelete == null || !confirmDelete) {
                throw new ServiceException(
                        "Delete operation requires explicit confirmation", HttpStatusCodes.BAD_REQUEST);
            }
        }
    }

    private JsonObject processBatchOperation(JsonObject requestData) {
        try {
            String operation = requestData.getString(OPERATION_KEY);

            // Simulate batch database operations (non-blocking)
            int recordsProcessed = RANDOM.nextInt(100) + appConfig.validation().batchProcessedRecords();

            return new JsonObject()
                    .put(OPERATION_KEY, operation)
                    .put(
                            ProductConstants.KEY_PROCESSED_AT,
                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .put("recordsProcessed", recordsProcessed)
                    .put(JsonKeys.STATUS, OutcomeConstants.COMPLETED);

        } catch (Exception e) {
            throw new ServiceException(
                    "Batch operation failed: " + e.getMessage(), HttpStatusCodes.SERVICE_UNAVAILABLE);
        }
    }
}
