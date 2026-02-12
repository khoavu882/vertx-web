package com.github.kaivu.vertxweb.patterns;

public interface CircuitBreakerObserver {
    void onTransition(String breakerName, CircuitBreaker.State from, CircuitBreaker.State to);

    void onFailure(String breakerName, Throwable failure);
}
