package com.github.kaivu.vertxweb.observability.metrics;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.time.Duration;
import java.util.Locale;

@Singleton
public class MicrometerMetricsFacade implements MetricsFacade, MetricsScrapeEndpoint {
    private static final String METRIC_HTTP_REQUESTS_TOTAL = "http_server_requests_total";
    private static final String METRIC_HTTP_REQUEST_DURATION = "http_server_request_duration_ms";
    private static final String METRIC_EVENTBUS_REQUESTS_TOTAL = "eventbus_requests_total";
    private static final String METRIC_EVENTBUS_REQUEST_DURATION = "eventbus_request_duration_ms";
    private static final String METRIC_CIRCUIT_BREAKER_TRANSITIONS_TOTAL = "circuit_breaker_transitions_total";
    private static final String METRIC_CIRCUIT_BREAKER_FAILURES_TOTAL = "circuit_breaker_failures_total";

    private final PrometheusMeterRegistry registry;

    @Inject
    public MicrometerMetricsFacade(ApplicationConfig appConfig) {
        this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        if (appConfig.observability().metrics().includeJvm()) {
            new ClassLoaderMetrics().bindTo(registry);
            new JvmMemoryMetrics().bindTo(registry);
            new JvmThreadMetrics().bindTo(registry);
        }

        if (appConfig.observability().metrics().includeSystem()) {
            new ProcessorMetrics().bindTo(registry);
            new UptimeMetrics().bindTo(registry);
        }
    }

    @Override
    public void recordHttpRequest(String route, String method, int status, long durationMs) {
        String safeRoute = normalizeTagValue(route, "unknown");
        String safeMethod = normalizeTagValue(method, "UNKNOWN");
        String safeStatus = Integer.toString(status);

        Counter.builder(METRIC_HTTP_REQUESTS_TOTAL)
                .tag("route", safeRoute)
                .tag("method", safeMethod)
                .tag("status", safeStatus)
                .register(registry)
                .increment();

        Timer.builder(METRIC_HTTP_REQUEST_DURATION)
                .tag("route", safeRoute)
                .tag("method", safeMethod)
                .tag("status", safeStatus)
                .register(registry)
                .record(Duration.ofMillis(Math.max(0, durationMs)));
    }

    @Override
    public void recordEventBusRequest(String address, String outcome, long durationMs) {
        String safeAddress = normalizeTagValue(address, "unknown");
        String safeOutcome = normalizeTagValue(outcome, "unknown");

        Counter.builder(METRIC_EVENTBUS_REQUESTS_TOTAL)
                .tag("address", safeAddress)
                .tag("outcome", safeOutcome)
                .register(registry)
                .increment();

        Timer.builder(METRIC_EVENTBUS_REQUEST_DURATION)
                .tag("address", safeAddress)
                .tag("outcome", safeOutcome)
                .register(registry)
                .record(Duration.ofMillis(Math.max(0, durationMs)));
    }

    @Override
    public void recordCircuitBreakerTransition(String breaker, String from, String to) {
        Counter.builder(METRIC_CIRCUIT_BREAKER_TRANSITIONS_TOTAL)
                .tag("breaker", normalizeTagValue(breaker, "unknown"))
                .tag("from", normalizeTagValue(from, "unknown"))
                .tag("to", normalizeTagValue(to, "unknown"))
                .register(registry)
                .increment();
    }

    @Override
    public void recordCircuitBreakerFailure(String breaker, String type) {
        Counter.builder(METRIC_CIRCUIT_BREAKER_FAILURES_TOTAL)
                .tag("breaker", normalizeTagValue(breaker, "unknown"))
                .tag("type", normalizeTagValue(type, "unknown"))
                .register(registry)
                .increment();
    }

    @Override
    public String scrape() {
        return registry.scrape();
    }

    @Override
    public String contentType() {
        return "text/plain; version=0.0.4; charset=utf-8";
    }

    MeterRegistry registry() {
        return registry;
    }

    private String normalizeTagValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 120) {
            return normalized.substring(0, 120);
        }
        return normalized;
    }
}
