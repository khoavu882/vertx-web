package com.github.kaivu.vertxweb.observability.health;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.*;
import com.github.kaivu.vertxweb.observability.metrics.MetricsFacade;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import java.util.Map;

@Singleton
public class EventBusReadinessCheck implements HealthCheck {
    private final Vertx vertx;
    private final ApplicationConfig appConfig;
    private final MetricsFacade metricsFacade;

    @Inject
    public EventBusReadinessCheck(Vertx vertx, ApplicationConfig appConfig, MetricsFacade metricsFacade) {
        this.vertx = vertx;
        this.appConfig = appConfig;
        this.metricsFacade = metricsFacade;
    }

    @Override
    public String name() {
        return "eventbus";
    }

    @Override
    public CheckType type() {
        return CheckType.READINESS;
    }

    @Override
    public Uni<CheckResult> execute() {
        String healthCheckAddress = appConfig.eventBus().healthCheckAddress();
        long startedAt = System.currentTimeMillis();
        DeliveryOptions options = new DeliveryOptions()
                .setSendTimeout(appConfig.observability().health().checkTimeoutMs());
        JsonObject payload = new JsonObject()
                .put(JsonKeys.TYPE, HealthConstants.VALUE_TYPE_HEALTH_CHECK)
                .put(JsonKeys.TIMESTAMP, startedAt);

        return Uni.createFrom()
                .completionStage(() -> vertx.eventBus()
                        .<JsonObject>request(healthCheckAddress, payload, options)
                        .toCompletionStage())
                .onItem()
                .transform(ignored -> {
                    long durationMs = System.currentTimeMillis() - startedAt;
                    metricsFacade.recordEventBusRequest(healthCheckAddress, OutcomeConstants.SUCCESS, durationMs);
                    return CheckResult.up(name(), type(), Map.of(HealthConstants.KEY_RESPONSE_TIME_MS, durationMs));
                })
                .onFailure()
                .recoverWithItem(error -> {
                    long durationMs = System.currentTimeMillis() - startedAt;
                    metricsFacade.recordEventBusRequest(healthCheckAddress, OutcomeConstants.ERROR, durationMs);
                    return CheckResult.down(
                            name(),
                            type(),
                            error.getMessage() != null ? error.getMessage() : "eventbus readiness check failed",
                            Map.of(HealthConstants.KEY_RESPONSE_TIME_MS, durationMs));
                });
    }
}
