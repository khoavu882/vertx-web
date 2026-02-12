package com.github.kaivu.vertxweb.middlewares;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.observability.metrics.MetricsFacade;
import com.github.kaivu.vertxweb.observability.tracing.TracingService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.opentelemetry.api.trace.Span;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Khoa Vu.
 * Mail: kai.vu.dev@gmail.com
 * Date: 9/12/24
 * Time: 10:20 AM
 */
@Singleton
public class LoggingHandler {

    private static final int MAX_LOG_BODY_CHARS = 2048;
    private static final Logger log = LoggerFactory.getLogger(LoggingHandler.class);
    private final ApplicationConfig applicationConfig;
    private final MetricsFacade metricsFacade;
    private final TracingService tracingService;

    @Inject
    public LoggingHandler(
            ApplicationConfig applicationConfig, MetricsFacade metricsFacade, TracingService tracingService) {
        this.applicationConfig = applicationConfig;
        this.metricsFacade = metricsFacade;
        this.tracingService = tracingService;
    }

    public void logRequest(RoutingContext ctx) {
        long startTime = System.currentTimeMillis();
        Span serverSpan = tracingService.startHttpServerSpan(ctx);
        String traceId = tracingService.getTraceId(serverSpan);
        String spanId = tracingService.getSpanId(serverSpan);
        if (traceId != null) {
            ctx.put(TracingService.ROUTING_TRACE_ID_KEY, traceId);
            ctx.response().putHeader(TracingService.HEADER_TRACE_ID, traceId);
        }
        if (spanId != null) {
            ctx.put(TracingService.ROUTING_SPAN_ID_KEY, spanId);
        }

        ctx.addEndHandler(endHandler -> {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = ctx.response().getStatusCode();
            String method = ctx.request().method().name();
            String path = ctx.request().path();
            String routePath = ctx.currentRoute() != null ? ctx.currentRoute().getPath() : path;

            if (applicationConfig.logging().enableRequestLogging()) {
                log.info("Request: {}, {} - Status: {} - Duration: {} ms", method, path, statusCode, duration);
            }
            metricsFacade.recordHttpRequest(routePath, method, statusCode, duration);
            tracingService.endHttpServerSpan(serverSpan, ctx, duration);

            if (applicationConfig.logging().enableRequestLogging()
                    && applicationConfig.logging().logRequestBodies()
                    && ctx.body() != null
                    && ctx.body().available()) {
                String body = ctx.body().asString();
                if (body != null && !body.isBlank()) {
                    String bodyPreview = body.length() > MAX_LOG_BODY_CHARS
                            ? body.substring(0, MAX_LOG_BODY_CHARS) + "...(truncated)"
                            : body;
                    log.debug("Request body: {}", bodyPreview);
                }
            }
        });

        ctx.next(); // Continue with the next handler
    }
}
