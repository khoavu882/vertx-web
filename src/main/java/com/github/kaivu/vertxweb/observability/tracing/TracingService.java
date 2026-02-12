package com.github.kaivu.vertxweb.observability.tracing;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.context.CorrelationContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TracingService {
    private static final Logger log = LoggerFactory.getLogger(TracingService.class);

    private static final String INSTRUMENTATION_NAME = "com.github.kaivu.vertxweb";
    private static final String SERVICE_NAME_ATTRIBUTE = "service.name";

    public static final String HEADER_TRACE_ID = "X-Trace-ID";
    public static final String ROUTING_TRACE_ID_KEY = "traceId";
    public static final String ROUTING_SPAN_ID_KEY = "spanId";
    public static final String EVENTBUS_TRACE_ID = "_traceId";
    public static final String EVENTBUS_PARENT_SPAN_ID = "_parentSpanId";

    private static final AttributeKey<String> HTTP_METHOD = AttributeKey.stringKey("http.method");
    private static final AttributeKey<String> HTTP_ROUTE = AttributeKey.stringKey("http.route");
    private static final AttributeKey<String> HTTP_TARGET = AttributeKey.stringKey("http.target");
    private static final AttributeKey<Long> HTTP_STATUS_CODE = AttributeKey.longKey("http.status_code");
    private static final AttributeKey<Long> HTTP_DURATION_MS = AttributeKey.longKey("http.server.duration_ms");
    private static final AttributeKey<String> EVENTBUS_DESTINATION = AttributeKey.stringKey("messaging.destination");

    private static final TextMapGetter<RoutingContext> ROUTING_CONTEXT_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(RoutingContext carrier) {
            return carrier.request().headers().names();
        }

        @Override
        public String get(RoutingContext carrier, String key) {
            return carrier.request().getHeader(key);
        }
    };

    private final boolean enabled;
    private final Tracer tracer;
    private final OpenTelemetry openTelemetry;
    private final SdkTracerProvider sdkTracerProvider;

    @Inject
    public TracingService(ApplicationConfig appConfig) {
        ApplicationConfig.ObservabilityConfig.TracingConfig tracingConfig =
                appConfig.observability().tracing();
        this.enabled = tracingConfig.enable();

        if (!enabled) {
            this.openTelemetry = OpenTelemetry.noop();
            this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);
            this.sdkTracerProvider = null;
            log.info("Distributed tracing is disabled by configuration.");
            return;
        }

        SpanExporter spanExporter = createSpanExporter(tracingConfig);
        Sampler sampler =
                Sampler.parentBased(Sampler.traceIdRatioBased(sanitizeSamplingRatio(tracingConfig.samplingRatio())));
        Resource resource = Resource.getDefault().toBuilder()
                .put(SERVICE_NAME_ATTRIBUTE, tracingConfig.serviceName())
                .build();

        this.sdkTracerProvider = SdkTracerProvider.builder()
                .setSampler(sampler)
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .build();

        this.openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(
                        io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.getInstance()))
                .build();
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "opentelemetry-shutdown"));
        log.info(
                "Distributed tracing initialized: serviceName={}, exporter={}, samplingRatio={}",
                tracingConfig.serviceName(),
                tracingConfig.exporter(),
                sanitizeSamplingRatio(tracingConfig.samplingRatio()));
    }

    public Span startHttpServerSpan(RoutingContext ctx) {
        if (!enabled) {
            return Span.getInvalid();
        }

        Context parentContext = openTelemetry
                .getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), ctx, ROUTING_CONTEXT_GETTER);

        String method = ctx.request().method().name();
        String path = ctx.request().path();

        Span span = tracer.spanBuilder(method + " " + path)
                .setSpanKind(SpanKind.SERVER)
                .setParent(parentContext)
                .startSpan();
        span.setAttribute(HTTP_METHOD, method);
        span.setAttribute(HTTP_TARGET, path);
        String routePath = ctx.currentRoute() != null ? ctx.currentRoute().getPath() : path;
        span.setAttribute(HTTP_ROUTE, routePath);
        return span;
    }

    public Span startEventBusConsumerSpan(String address, JsonObject message) {
        if (!enabled) {
            return Span.getInvalid();
        }

        Context parent = extractEventBusParent(message);
        Span span = tracer.spanBuilder("eventbus " + address)
                .setSpanKind(SpanKind.CONSUMER)
                .setParent(parent)
                .startSpan();
        span.setAttribute(EVENTBUS_DESTINATION, address);
        return span;
    }

    public void endHttpServerSpan(Span span, RoutingContext ctx, long durationMs) {
        if (!isSpanValid(span)) {
            return;
        }

        int statusCode = ctx.response().getStatusCode();
        span.setAttribute(HTTP_STATUS_CODE, (long) statusCode);
        span.setAttribute(HTTP_DURATION_MS, durationMs);

        Throwable failure = ctx.failure();
        if (failure != null) {
            span.recordException(failure);
            span.setStatus(StatusCode.ERROR, failure.getMessage());
        } else if (statusCode >= 500) {
            span.setStatus(StatusCode.ERROR, "HTTP " + statusCode);
        }

        span.end();
    }

    public void endSpan(Span span, Throwable failure) {
        if (!isSpanValid(span)) {
            return;
        }
        if (failure != null) {
            span.recordException(failure);
            span.setStatus(StatusCode.ERROR, failure.getMessage());
        }
        span.end();
    }

    public void enrichCorrelationContext(CorrelationContext correlationContext, Span span) {
        if (correlationContext == null || !isSpanValid(span)) {
            return;
        }
        correlationContext
                .withTraceId(span.getSpanContext().getTraceId())
                .withSpanId(span.getSpanContext().getSpanId());
    }

    public void injectEventBusTraceContext(JsonObject message, CorrelationContext correlationContext) {
        if (!enabled || message == null || correlationContext == null) {
            return;
        }
        if (isValidTraceId(correlationContext.getTraceId())) {
            message.put(EVENTBUS_TRACE_ID, correlationContext.getTraceId());
        }
        if (isValidSpanId(correlationContext.getSpanId())) {
            message.put(EVENTBUS_PARENT_SPAN_ID, correlationContext.getSpanId());
        }
    }

    public String getTraceId(Span span) {
        if (!isSpanValid(span)) {
            return null;
        }
        return span.getSpanContext().getTraceId();
    }

    public String getSpanId(Span span) {
        if (!isSpanValid(span)) {
            return null;
        }
        return span.getSpanContext().getSpanId();
    }

    private SpanExporter createSpanExporter(ApplicationConfig.ObservabilityConfig.TracingConfig tracingConfig) {
        String exporter = tracingConfig.exporter();
        if ("otlp".equalsIgnoreCase(exporter)) {
            OtlpGrpcSpanExporterBuilder builder = OtlpGrpcSpanExporter.builder();
            String endpoint = tracingConfig.otlpEndpoint();
            if (endpoint != null && !endpoint.isBlank()) {
                builder.setEndpoint(endpoint);
            }
            return builder.build();
        }
        return new LoggingSpanExporter();
    }

    private Context extractEventBusParent(JsonObject message) {
        if (message == null) {
            return Context.current();
        }

        String traceId = message.getString(EVENTBUS_TRACE_ID);
        String parentSpanId = message.getString(EVENTBUS_PARENT_SPAN_ID);
        if (!isValidTraceId(traceId) || !isValidSpanId(parentSpanId)) {
            return Context.current();
        }

        SpanContext remoteParent = SpanContext.createFromRemoteParent(
                traceId, parentSpanId, TraceFlags.getSampled(), TraceState.getDefault());
        return Context.current().with(Span.wrap(remoteParent));
    }

    private double sanitizeSamplingRatio(double samplingRatio) {
        if (samplingRatio < 0.0d) {
            return 0.0d;
        }
        if (samplingRatio > 1.0d) {
            return 1.0d;
        }
        return samplingRatio;
    }

    private boolean isSpanValid(Span span) {
        return span != null && span.getSpanContext().isValid();
    }

    private boolean isValidTraceId(String traceId) {
        return traceId != null && traceId.matches("^[0-9a-f]{32}$");
    }

    private boolean isValidSpanId(String spanId) {
        return spanId != null && spanId.matches("^[0-9a-f]{16}$");
    }

    private void shutdown() {
        if (sdkTracerProvider == null) {
            return;
        }
        try {
            sdkTracerProvider.shutdown();
        } catch (Exception e) {
            log.warn("Failed to shutdown tracer provider cleanly", e);
        }
    }
}
