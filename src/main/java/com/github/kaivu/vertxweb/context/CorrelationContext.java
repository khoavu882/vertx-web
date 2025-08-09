package com.github.kaivu.vertxweb.context;

import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Production-grade correlation context for tracking requests across services.
 * Follows distributed tracing best practices used in microservices.
 */
public class CorrelationContext {

    // Standard correlation headers
    public static final String CORRELATION_ID = "correlationId";
    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String TENANT_ID = "tenantId";
    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";
    public static final String SESSION_ID = "sessionId";

    // Timing and metadata
    public static final String START_TIME = "startTime";
    public static final String SOURCE_IP = "sourceIp";
    public static final String USER_AGENT = "userAgent";

    private final Map<String, Object> context;

    private CorrelationContext(Map<String, Object> context) {
        this.context = new HashMap<>(context);
    }

    public static CorrelationContext create() {
        Map<String, Object> context = new HashMap<>();
        context.put(CORRELATION_ID, UUID.randomUUID().toString());
        context.put(REQUEST_ID, UUID.randomUUID().toString());
        context.put(START_TIME, Instant.now().toEpochMilli());
        return new CorrelationContext(context);
    }

    public static CorrelationContext fromJson(JsonObject json) {
        Map<String, Object> context = new HashMap<>();
        if (json != null) {
            json.forEach(entry -> context.put(entry.getKey(), entry.getValue()));
        }
        return new CorrelationContext(context);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        context.forEach(json::put);
        return json;
    }

    // Getters
    public String getCorrelationId() {
        return (String) context.get(CORRELATION_ID);
    }

    public String getRequestId() {
        return (String) context.get(REQUEST_ID);
    }

    public String getUserId() {
        return (String) context.get(USER_ID);
    }

    public String getTenantId() {
        return (String) context.get(TENANT_ID);
    }

    public String getTraceId() {
        return (String) context.get(TRACE_ID);
    }

    public Long getStartTime() {
        return (Long) context.get(START_TIME);
    }

    // Setters with builder pattern
    public CorrelationContext withUserId(String userId) {
        context.put(USER_ID, userId);
        return this;
    }

    public CorrelationContext withTenantId(String tenantId) {
        context.put(TENANT_ID, tenantId);
        return this;
    }

    public CorrelationContext withTraceId(String traceId) {
        context.put(TRACE_ID, traceId);
        return this;
    }

    public CorrelationContext withSpanId(String spanId) {
        context.put(SPAN_ID, spanId);
        return this;
    }

    public CorrelationContext withSessionId(String sessionId) {
        context.put(SESSION_ID, sessionId);
        return this;
    }

    public CorrelationContext withSourceIp(String sourceIp) {
        context.put(SOURCE_IP, sourceIp);
        return this;
    }

    public CorrelationContext withUserAgent(String userAgent) {
        context.put(USER_AGENT, userAgent);
        return this;
    }

    public CorrelationContext withAttribute(String key, Object value) {
        if (key != null && value != null) {
            context.put(key, value);
        }
        return this;
    }

    public Object getAttribute(String key) {
        return context.get(key);
    }

    /**
     * Sets up logging context (MDC) for structured logging.
     * Call this when processing starts in any service/verticle.
     */
    public void setupLoggingContext() {
        MDC.put(CORRELATION_ID, getCorrelationId());
        MDC.put(REQUEST_ID, getRequestId());
        if (getUserId() != null) MDC.put(USER_ID, getUserId());
        if (getTenantId() != null) MDC.put(TENANT_ID, getTenantId());
        if (getTraceId() != null) MDC.put(TRACE_ID, getTraceId());
    }

    /**
     * Clears logging context. Call this when processing completes.
     */
    public void clearLoggingContext() {
        MDC.clear();
    }

    /**
     * Creates a child context for sub-operations (e.g., worker tasks).
     * Preserves correlation info but generates new span/request IDs.
     */
    public CorrelationContext createChild(String operation) {
        Map<String, Object> childContext = new HashMap<>(this.context);
        childContext.put(REQUEST_ID, UUID.randomUUID().toString());
        childContext.put(SPAN_ID, UUID.randomUUID().toString());
        childContext.put("parentSpanId", context.get(SPAN_ID));
        childContext.put("operation", operation);
        return new CorrelationContext(childContext);
    }

    /**
     * Calculates processing duration from start time.
     */
    public long getProcessingDurationMs() {
        Long startTime = getStartTime();
        return startTime != null ? Instant.now().toEpochMilli() - startTime : 0;
    }

    @Override
    public String toString() {
        return String.format(
                "CorrelationContext{correlationId=%s, requestId=%s, userId=%s}",
                getCorrelationId(), getRequestId(), getUserId());
    }
}
