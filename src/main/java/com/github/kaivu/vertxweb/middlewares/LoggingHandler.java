package com.github.kaivu.vertxweb.middlewares;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Khoa Vu.
 * Mail: khoavd12@fpt.com
 * Date: 9/12/24
 * Time: 10:20 AM
 */
@Singleton
public class LoggingHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingHandler.class);
    private final ApplicationConfig applicationConfig;

    @Inject
    public LoggingHandler(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
    }

    public void logRequest(RoutingContext ctx) {
        // Only log requests if request logging is enabled
        if (!applicationConfig.logging().enableRequestLogging()) {
            ctx.next();
            return;
        }

        long startTime = System.currentTimeMillis();
        ctx.addEndHandler(endHandler -> {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = ctx.response().getStatusCode();
            String method = ctx.request().method().name();
            String path = ctx.request().path();

            log.info("Request: {}, {} - Status: {} - Duration: {} ms", method, path, statusCode, duration);
        });

        ctx.next(); // Continue with the next handler
    }
}
