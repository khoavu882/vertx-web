package com.github.kaivu.vertxweb.patterns;

import java.time.Instant;

public record CircuitBreakerMetrics(
        String name, CircuitBreaker.State state, int failureCount, int successCount, long lastFailureTime) {

    public boolean isOpen() {
        return state == CircuitBreaker.State.OPEN;
    }

    public boolean isClosed() {
        return state == CircuitBreaker.State.CLOSED;
    }

    public boolean isHalfOpen() {
        return state == CircuitBreaker.State.HALF_OPEN;
    }

    public String getLastFailureTimeFormatted() {
        if (lastFailureTime == 0) {
            return "Never";
        }
        return Instant.ofEpochMilli(lastFailureTime).toString();
    }

    public long getTimeSinceLastFailure() {
        if (lastFailureTime == 0) {
            return -1;
        }
        return System.currentTimeMillis() - lastFailureTime;
    }
}
