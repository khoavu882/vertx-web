package com.github.kaivu.vertxweb.consumers;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationConsumer implements EventBusConsumer {

    private static final Logger log = LoggerFactory.getLogger(BatchOperationConsumer.class);
    private static final String OPERATION_KEY = "operation";
    private static final Random RANDOM = new Random();
    private final ApplicationConfig appConfig;

    @Inject
    public BatchOperationConsumer(ApplicationConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public String getEventAddress() {
        return "app.worker.batch-operation";
    }

    @Override
    public void registerConsumer(EventBus eventBus) {
        eventBus.<JsonObject>consumer(getEventAddress(), this::handle);
    }

    public void handle(Message<JsonObject> message) {
        try {
            JsonObject requestData = message.body();
            String operation = requestData.getString(OPERATION_KEY);

            log.info("Starting batch operation: {}", operation);

            // Validate batch request
            validateBatchRequest(requestData);

            // Process batch operation
            JsonObject result = processBatchOperation(requestData);

            log.info("Batch operation completed: {}", operation);
            message.reply(result.encode());

        } catch (ServiceException e) {
            log.error("Service error in batch operation: {}", e.getMessage());
            message.fail(e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in batch operation", e);
            message.fail(AppConstants.Status.INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }

    private void validateBatchRequest(JsonObject requestData) {
        if (requestData == null) {
            throw new ServiceException("Batch request data cannot be null", AppConstants.Status.BAD_REQUEST);
        }

        String operation = requestData.getString(OPERATION_KEY);
        if (operation == null || operation.trim().isEmpty()) {
            throw new ServiceException(
                    "Operation type is required for batch processing", AppConstants.Status.BAD_REQUEST);
        }

        // Validate allowed operations
        switch (operation.toLowerCase()) {
            case "insert":
            case "update":
            case "delete":
            case "migrate":
                break;
            default:
                throw new ServiceException(
                        "Unsupported batch operation: " + operation
                                + ". Allowed operations: insert, update, delete, migrate",
                        AppConstants.Status.BAD_REQUEST);
        }

        // Additional validation based on operation type
        if (operation.equalsIgnoreCase("delete")) {
            Boolean confirmDelete = requestData.getBoolean("confirmDelete");
            if (confirmDelete == null || !confirmDelete) {
                throw new ServiceException(
                        "Delete operation requires explicit confirmation", AppConstants.Status.BAD_REQUEST);
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
                    .put("processedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .put("recordsProcessed", recordsProcessed)
                    .put("status", "completed");

        } catch (Exception e) {
            throw new ServiceException(
                    "Batch operation failed: " + e.getMessage(), AppConstants.Status.SERVICE_UNAVAILABLE);
        }
    }
}
