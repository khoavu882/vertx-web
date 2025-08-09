package com.github.kaivu.vertxweb.middlewares;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Khoa Vu.
 * Mail: khoavd12@fpt.com
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

        if (failure instanceof ServiceException ex) {
            statusCode = ex.getStatusCode();
            message = failure.getMessage();
            log.warn("Service exception: {}", message);
        } else if (failure != null) {
            message = failure.getMessage();
            log.error("Unexpected error", failure);
        }

        // Log error details based on configuration
        String path = ctx.request().path();
        String method = ctx.request().method().name();
        log.error("Error handling request: {} {} - Status: {} - Message: {}", method, path, statusCode, message);

        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                        .put("error", message)
                        .put("status", statusCode)
                        .encode());
    }
}
