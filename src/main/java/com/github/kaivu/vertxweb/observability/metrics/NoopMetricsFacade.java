package com.github.kaivu.vertxweb.observability.metrics;

import com.google.inject.Singleton;

/**
 * Phase 1 foundation implementation.
 *
 * This no-op implementation preserves existing behavior while establishing
 * a stable instrumentation contract for later Micrometer integration.
 */
@Singleton
public class NoopMetricsFacade implements MetricsFacade, MetricsScrapeEndpoint {
    @Override
    public void recordHttpRequest(String route, String method, int status, long durationMs) {
        // Phase 1 foundation: intentionally no-op.
    }

    @Override
    public void recordEventBusRequest(String address, String outcome, long durationMs) {
        // Phase 1 foundation: intentionally no-op.
    }

    @Override
    public void recordCircuitBreakerTransition(String breaker, String from, String to) {
        // Phase 1 foundation: intentionally no-op.
    }

    @Override
    public void recordCircuitBreakerFailure(String breaker, String type) {
        // Phase 1 foundation: intentionally no-op.
    }

    @Override
    public String scrape() {
        return "# metrics are disabled\n";
    }

    @Override
    public String contentType() {
        return "text/plain; charset=utf-8";
    }
}
