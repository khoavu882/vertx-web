package com.github.kaivu.vertx_web.middlewares;

import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Khoa Vu.
 * Mail: khoavd12@fpt.com
 * Date: 9/12/24
 * Time: 10:20 AM
 */
public class LoggingHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingHandler.class);

    private LoggingHandler() {
        // Private constructor to prevent instantiation
    }

    public static void logRequest(RoutingContext ctx) {
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
