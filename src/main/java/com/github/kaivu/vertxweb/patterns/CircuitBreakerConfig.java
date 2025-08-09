package com.github.kaivu.vertxweb.patterns;

public record CircuitBreakerConfig(int failureThreshold, int successThreshold, long timeoutMs, long resetTimeoutMs) {

    public static CircuitBreakerConfig defaultConfig() {
        return new CircuitBreakerConfig(
                5, // failureThreshold: 5 failures to open circuit
                3, // successThreshold: 3 successes to close circuit from half-open
                10000, // timeoutMs: 10 seconds timeout per operation
                60000 // resetTimeoutMs: 1 minute before attempting reset
                );
    }

    public static CircuitBreakerConfig create(
            int failureThreshold, int successThreshold, long timeoutMs, long resetTimeoutMs) {
        return new CircuitBreakerConfig(failureThreshold, successThreshold, timeoutMs, resetTimeoutMs);
    }

    public static CircuitBreakerConfig fastFail() {
        return new CircuitBreakerConfig(
                3, // failureThreshold: 3 failures to open circuit (more sensitive)
                2, // successThreshold: 2 successes to close circuit
                5000, // timeoutMs: 5 seconds timeout (shorter)
                30000 // resetTimeoutMs: 30 seconds before attempting reset (shorter)
                );
    }

    public static CircuitBreakerConfig resilient() {
        return new CircuitBreakerConfig(
                10, // failureThreshold: 10 failures to open circuit (more tolerant)
                5, // successThreshold: 5 successes to close circuit
                15000, // timeoutMs: 15 seconds timeout (longer)
                120000 // resetTimeoutMs: 2 minutes before attempting reset (longer)
                );
    }
}
