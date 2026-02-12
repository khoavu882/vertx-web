package com.github.kaivu.vertxweb.constants;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class HttpConstants {
    private HttpConstants() {}

    public static final String CONTENT_TYPE_JSON = HttpHeaderValues.APPLICATION_JSON.toString();
    public static final String CONTENT_TYPE_TEXT = HttpHeaderValues.TEXT_PLAIN.toString();
    public static final String CONTENT_TYPE_HTML = HttpHeaderValues.TEXT_HTML.toString();
    public static final String CHARSET_UTF8 =
            "charset=" + StandardCharsets.UTF_8.name().toLowerCase(Locale.ROOT);
    public static final String AUTHORIZATION = HttpHeaders.AUTHORIZATION.toString();
    public static final String X_TENANT_ID = "X-Tenant-ID";
    public static final String X_CORRELATION_ID = "X-Correlation-ID";
    public static final String X_ERROR_ID = "X-Error-ID";
    public static final String X_AUTH_MODE = "X-Auth-Mode";
    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";
}
