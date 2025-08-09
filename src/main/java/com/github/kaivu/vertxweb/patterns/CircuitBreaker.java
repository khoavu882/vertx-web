package com.github.kaivu.vertxweb.patterns;

import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State {
        CLOSED, // Normal operation
        OPEN, // Circuit is open, failing fast
        HALF_OPEN // Testing if service is back
    }

    private final String name;
    private final int failureThreshold;
    private final int successThreshold;
    private final Duration timeout;
    private final Duration resetTimeout;
    private final Vertx vertx;

    private final AtomicReference<State> state;
    private final AtomicInteger failureCount;
    private final AtomicInteger successCount;
    private final AtomicLong lastFailureTime;

    public CircuitBreaker(String name, Vertx vertx, CircuitBreakerConfig config) {
        this.name = name;
        this.vertx = vertx;
        this.failureThreshold = config.failureThreshold();
        this.successThreshold = config.successThreshold();
        this.timeout = Duration.ofMillis(config.timeoutMs());
        this.resetTimeout = Duration.ofMillis(config.resetTimeoutMs());

        this.state = new AtomicReference<>(State.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.lastFailureTime = new AtomicLong(0);
    }

    public <T> Uni<T> execute(Supplier<Uni<T>> operation) {
        if (shouldAllowRequest()) {
            return executeOperation(operation);
        } else {
            return Uni.createFrom().failure(new CircuitBreakerOpenException("Circuit breaker '" + name + "' is OPEN"));
        }
    }

    private boolean shouldAllowRequest() {
        State currentState = state.get();

        switch (currentState) {
            case CLOSED:
                return true;
            case OPEN:
                if (shouldAttemptReset()) {
                    log.info("Circuit breaker '{}' transitioning to HALF_OPEN", name);
                    state.set(State.HALF_OPEN);
                    return true;
                }
                return false;
            case HALF_OPEN:
                return true;
            default:
                return false;
        }
    }

    private boolean shouldAttemptReset() {
        long lastFailure = lastFailureTime.get();
        return lastFailure > 0 && Instant.now().toEpochMilli() - lastFailure >= resetTimeout.toMillis();
    }

    private <T> Uni<T> executeOperation(Supplier<Uni<T>> operation) {
        Instant start = Instant.now();

        return operation
                .get()
                .ifNoItem()
                .after(timeout)
                .failWith(new CircuitBreakerTimeoutException("Operation timed out after " + timeout.toMillis() + "ms"))
                .invoke(result -> onSuccess(start))
                .onFailure()
                .invoke(failure -> onFailure(start, failure));
    }

    private void onSuccess(Instant start) {
        Duration duration = Duration.between(start, Instant.now());
        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            int currentSuccessCount = successCount.incrementAndGet();
            log.debug(
                    "Circuit breaker '{}' success in HALF_OPEN state: {}/{}",
                    name,
                    currentSuccessCount,
                    successThreshold);

            if (currentSuccessCount >= successThreshold) {
                reset();
                log.info("Circuit breaker '{}' transitioning to CLOSED after {} successes", name, currentSuccessCount);
            }
        } else if (currentState == State.CLOSED) {
            // Reset failure count on success in CLOSED state
            failureCount.set(0);
        }

        log.debug("Circuit breaker '{}' operation succeeded in {}ms", name, duration.toMillis());
    }

    private void onFailure(Instant start, Throwable failure) {
        Duration duration = Duration.between(start, Instant.now());
        State currentState = state.get();

        lastFailureTime.set(Instant.now().toEpochMilli());

        if (currentState == State.HALF_OPEN) {
            // Any failure in HALF_OPEN should open the circuit
            openCircuit();
            log.warn(
                    "Circuit breaker '{}' transitioning to OPEN after failure in HALF_OPEN state: {}",
                    name,
                    failure.getMessage());
        } else if (currentState == State.CLOSED) {
            int currentFailureCount = failureCount.incrementAndGet();
            log.debug("Circuit breaker '{}' failure count: {}/{}", name, currentFailureCount, failureThreshold);

            if (currentFailureCount >= failureThreshold) {
                openCircuit();
                log.warn("Circuit breaker '{}' transitioning to OPEN after {} failures", name, currentFailureCount);
            }
        }

        log.warn("Circuit breaker '{}' operation failed in {}ms: {}", name, duration.toMillis(), failure.getMessage());
    }

    private void openCircuit() {
        state.set(State.OPEN);
        successCount.set(0);
    }

    private void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        successCount.set(0);
    }

    public State getState() {
        return state.get();
    }

    public CircuitBreakerMetrics getMetrics() {
        return new CircuitBreakerMetrics(
                name, state.get(), failureCount.get(), successCount.get(), lastFailureTime.get());
    }

    // Exception classes
    public static class CircuitBreakerException extends RuntimeException {
        public CircuitBreakerException(String message) {
            super(message);
        }
    }

    public static class CircuitBreakerOpenException extends CircuitBreakerException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }

    public static class CircuitBreakerTimeoutException extends CircuitBreakerException {
        public CircuitBreakerTimeoutException(String message) {
            super(message);
        }
    }
}
