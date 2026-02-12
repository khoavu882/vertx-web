package com.github.kaivu.vertxweb.observability.health;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
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
    private static final String HEALTH_CHECK_EVENT = "app.health.check";

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
        long startedAt = System.currentTimeMillis();
        DeliveryOptions options = new DeliveryOptions()
                .setSendTimeout(appConfig.observability().health().checkTimeoutMs());
        JsonObject payload = new JsonObject().put("type", "health-check").put("timestamp", startedAt);

        return Uni.createFrom()
                .completionStage(() -> vertx.eventBus()
                        .<JsonObject>request(HEALTH_CHECK_EVENT, payload, options)
                        .toCompletionStage())
                .onItem()
                .transform(ignored -> {
                    long durationMs = System.currentTimeMillis() - startedAt;
                    metricsFacade.recordEventBusRequest(HEALTH_CHECK_EVENT, "success", durationMs);
                    return CheckResult.up(name(), type(), Map.of("responseTimeMs", durationMs));
                })
                .onFailure()
                .recoverWithItem(error -> {
                    long durationMs = System.currentTimeMillis() - startedAt;
                    metricsFacade.recordEventBusRequest(HEALTH_CHECK_EVENT, "error", durationMs);
                    return CheckResult.down(
                            name(),
                            type(),
                            error.getMessage() != null ? error.getMessage() : "eventbus readiness check failed",
                            Map.of("responseTimeMs", durationMs));
                });
    }
}
