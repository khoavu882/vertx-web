package com.github.kaivu.vertxweb.patterns;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CircuitBreakerRegistry {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerRegistry.class);

    private final Vertx vertx;
    private final ApplicationConfig appConfig;
    private final ConcurrentMap<String, CircuitBreaker> circuitBreakers;

    @Inject
    public CircuitBreakerRegistry(Vertx vertx, ApplicationConfig appConfig) {
        this.vertx = vertx;
        this.appConfig = appConfig;
        this.circuitBreakers = new ConcurrentHashMap<>();

        // Initialize common circuit breakers
        initializeDefaultCircuitBreakers();
    }

    private void initializeDefaultCircuitBreakers() {
        // Database operations circuit breaker
        createCircuitBreaker("database", CircuitBreakerConfig.defaultConfig());

        // External API circuit breaker (more sensitive)
        createCircuitBreaker("external-api", CircuitBreakerConfig.fastFail());

        // Analytics service circuit breaker (more resilient)
        createCircuitBreaker("analytics", CircuitBreakerConfig.resilient());

        // File I/O operations circuit breaker
        createCircuitBreaker(
                "file-io",
                CircuitBreakerConfig.create(
                        7, // 7 failures to open
                        3, // 3 successes to close
                        8000, // 8 seconds timeout
                        45000 // 45 seconds reset timeout
                        ));

        log.info("Initialized {} default circuit breakers", circuitBreakers.size());
    }

    public CircuitBreaker createCircuitBreaker(String name, CircuitBreakerConfig config) {
        CircuitBreaker circuitBreaker = new CircuitBreaker(name, vertx, config);
        circuitBreakers.put(name, circuitBreaker);
        log.info(
                "Created circuit breaker '{}' with config: failures={}, successes={}, timeout={}ms, resetTimeout={}ms",
                name,
                config.failureThreshold(),
                config.successThreshold(),
                config.timeoutMs(),
                config.resetTimeoutMs());
        return circuitBreaker;
    }

    public CircuitBreaker getCircuitBreaker(String name) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(name);
        if (circuitBreaker == null) {
            log.warn("Circuit breaker '{}' not found, creating with default config", name);
            circuitBreaker = createCircuitBreaker(name, CircuitBreakerConfig.defaultConfig());
        }
        return circuitBreaker;
    }

    public boolean hasCircuitBreaker(String name) {
        return circuitBreakers.containsKey(name);
    }

    public List<CircuitBreakerMetrics> getAllMetrics() {
        return circuitBreakers.values().stream().map(CircuitBreaker::getMetrics).toList();
    }

    public CircuitBreakerMetrics getMetrics(String name) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(name);
        return circuitBreaker != null ? circuitBreaker.getMetrics() : null;
    }

    public int getCircuitBreakerCount() {
        return circuitBreakers.size();
    }

    public List<String> getCircuitBreakerNames() {
        return circuitBreakers.keySet().stream().sorted().toList();
    }

    // Convenience methods for common circuit breakers
    public CircuitBreaker getDatabaseCircuitBreaker() {
        return getCircuitBreaker("database");
    }

    public CircuitBreaker getExternalApiCircuitBreaker() {
        return getCircuitBreaker("external-api");
    }

    public CircuitBreaker getAnalyticsCircuitBreaker() {
        return getCircuitBreaker("analytics");
    }

    public CircuitBreaker getFileIoCircuitBreaker() {
        return getCircuitBreaker("file-io");
    }
}
