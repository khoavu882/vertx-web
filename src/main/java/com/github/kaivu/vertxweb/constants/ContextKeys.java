package com.github.kaivu.vertxweb.constants;

public final class ContextKeys {
    private ContextKeys() {}

    public static final String ROUTING_CONTEXT_WRAPPER = "contextWrapper";
    public static final String EVENTBUS_CONTEXT = "_context";
    public static final String EVENTBUS_CORRELATION_ID = "_correlationId";
    public static final String EVENTBUS_REQUEST_ID = "_requestId";
}
