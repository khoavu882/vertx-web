package com.github.kaivu.vertxweb.observability.metrics;

public interface MetricsFacade {
    void recordHttpRequest(String route, String method, int status, long durationMs);

    void recordEventBusRequest(String address, String outcome, long durationMs);

    void recordCircuitBreakerTransition(String breaker, String from, String to);

    void recordCircuitBreakerFailure(String breaker, String type);
}
