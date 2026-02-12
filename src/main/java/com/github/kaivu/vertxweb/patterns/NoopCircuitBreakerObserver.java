package com.github.kaivu.vertxweb.patterns;

public class NoopCircuitBreakerObserver implements CircuitBreakerObserver {
    @Override
    public void onTransition(String breakerName, CircuitBreaker.State from, CircuitBreaker.State to) {
        // Intentionally no-op.
    }

    @Override
    public void onFailure(String breakerName, Throwable failure) {
        // Intentionally no-op.
    }
}
