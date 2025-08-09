package com.github.kaivu.vertxweb.consumers;

import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationConsumer implements EventBusConsumer {

    private static final Logger log = LoggerFactory.getLogger(BatchOperationConsumer.class);
    private static final String OPERATION_KEY = "operation";

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
            message.fail(500, "Internal server error: " + e.getMessage());
        }
    }

    private void validateBatchRequest(JsonObject requestData) {
        if (requestData == null) {
            throw new ServiceException("Batch request data cannot be null", 400);
        }

        String operation = requestData.getString(OPERATION_KEY);
        if (operation == null || operation.trim().isEmpty()) {
            throw new ServiceException("Operation type is required for batch processing", 400);
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
                        400);
        }

        // Additional validation based on operation type
        if (operation.equalsIgnoreCase("delete")) {
            Boolean confirmDelete = requestData.getBoolean("confirmDelete");
            if (confirmDelete == null || !confirmDelete) {
                throw new ServiceException("Delete operation requires explicit confirmation", 400);
            }
        }
    }

    private JsonObject processBatchOperation(JsonObject requestData) {
        try {
            String operation = requestData.getString(OPERATION_KEY);

            // Simulate batch database operations
            Thread.sleep(1500); // Simulate database batch processing

            return new JsonObject()
                    .put(OPERATION_KEY, operation)
                    .put("processedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .put("recordsProcessed", 1000)
                    .put("status", "completed");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Batch operation was interrupted", 503);
        }
    }
}
