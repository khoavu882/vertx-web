package com.github.kaivu.vertxweb.middlewares;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.context.ContextAwareVertxWrapper;
import com.github.kaivu.vertxweb.context.CorrelationContext;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Khoa Vu.
 * Mail: kai.vu.dev@gmail.com
 * Date: 9/12/24
 * Time: 9:37 AM
 */
@Singleton
public class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);
    private final ApplicationConfig applicationConfig;

    @Inject
    public ErrorHandler(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
    }

    public void handle(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        int statusCode = AppConstants.Status.INTERNAL_SERVER_ERROR;
        String message = "Internal Server Error";
        String errorId = UUID.randomUUID().toString();

        // Extract correlation context if available
        ContextAwareVertxWrapper wrapper = ctx.get("contextWrapper");
        String correlationId = null;
        if (wrapper != null) {
            CorrelationContext context = wrapper.getCorrelationContext();
            correlationId = context.getCorrelationId();
        }

        // Determine error type and status
        if (failure instanceof ServiceException ex) {
            statusCode = ex.getStatusCode();
            message = failure.getMessage();

            // Log service exceptions at appropriate level
            if (statusCode >= 500) {
                log.error("Service error [{}] correlationId={}: {}", errorId, correlationId, message, failure);
            } else {
                log.warn("Client error [{}] correlationId={}: {}", errorId, correlationId, message);
            }
        } else if (failure != null) {
            message = failure.getMessage() != null ? failure.getMessage() : "Unexpected server error";
            log.error("Unexpected error [{}] correlationId={}: {}", errorId, correlationId, message, failure);
        } else {
            // Handle cases where failure is null but status code indicates error
            int responseStatus = ctx.response().getStatusCode();
            if (responseStatus >= 400) {
                statusCode = responseStatus;
                message = getStandardErrorMessage(statusCode);
            }
            log.error("Error without throwable [{}] correlationId={}: status={}", errorId, correlationId, statusCode);
        }

        // Log request context for debugging
        String path = ctx.request().path();
        String method = ctx.request().method().name();
        String userAgent = ctx.request().getHeader("User-Agent");
        String sourceIp = ctx.request().remoteAddress() != null
                ? ctx.request().remoteAddress().host()
                : "unknown";

        log.info(
                "Error handling request [{}]: {} {} - Status: {} - IP: {} - UserAgent: {}",
                errorId,
                method,
                path,
                statusCode,
                sourceIp,
                userAgent);

        // Create comprehensive error response
        JsonObject errorResponse = new JsonObject()
                .put("error", message)
                .put("status", statusCode)
                .put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .put("path", path)
                .put("method", method)
                .put("errorId", errorId);

        // Add correlation ID if available
        if (correlationId != null) {
            errorResponse.put("correlationId", correlationId);
        }

        // Add additional debug info in development mode
        if (!applicationConfig.logging().logLevel().equalsIgnoreCase("PRODUCTION")) {
            errorResponse.put("stackTrace", getStackTrace(failure));
        }

        // Set appropriate headers and respond
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", AppConstants.Http.CONTENT_TYPE_JSON)
                .putHeader("X-Error-ID", errorId)
                .putHeader("X-Correlation-ID", correlationId != null ? correlationId : "none")
                .end(errorResponse.encode());
    }

    private String getStandardErrorMessage(int statusCode) {
        return switch (statusCode) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 410 -> "Gone";
            case 422 -> "Unprocessable Entity";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 501 -> "Not Implemented";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "HTTP Error " + statusCode;
        };
    }

    private String getStackTrace(Throwable failure) {
        if (failure == null) {
            return null;
        }

        StringBuilder stackTrace = new StringBuilder();
        stackTrace
                .append(failure.getClass().getSimpleName())
                .append(": ")
                .append(failure.getMessage())
                .append("\n");

        StackTraceElement[] elements = failure.getStackTrace();
        int maxElements = Math.min(10, elements.length); // Limit stack trace depth

        for (int i = 0; i < maxElements; i++) {
            stackTrace.append("\t").append(elements[i].toString()).append("\n");
        }

        if (elements.length > maxElements) {
            stackTrace.append("\t... ").append(elements.length - maxElements).append(" more\n");
        }

        return stackTrace.toString();
    }
}
