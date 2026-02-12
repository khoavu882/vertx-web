package com.github.kaivu.vertxweb.observability.health;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
public class DefaultProbeOrchestrator implements ProbeOrchestrator {
    private final HealthCheckRegistry healthCheckRegistry;

    @Inject
    public DefaultProbeOrchestrator(HealthCheckRegistry healthCheckRegistry) {
        this.healthCheckRegistry = healthCheckRegistry;
    }

    @Override
    public Uni<HealthPayload> live() {
        return executeByType(CheckType.LIVENESS);
    }

    @Override
    public Uni<HealthPayload> ready() {
        return executeByType(CheckType.READINESS);
    }

    @Override
    public Uni<HealthPayload> started() {
        return executeByType(CheckType.STARTUP);
    }

    @Override
    public Uni<HealthPayload> overall() {
        return Uni.join().all(ready(), started()).andCollectFailures().map(items -> {
            HealthPayload readyPayload = items.get(0);
            HealthPayload startupPayload = items.get(1);

            List<CheckResult> combinedChecks = new ArrayList<>();
            combinedChecks.addAll(readyPayload.checks());
            combinedChecks.addAll(startupPayload.checks());
            return HealthPayload.fromChecks(combinedChecks);
        });
    }

    private Uni<HealthPayload> executeByType(CheckType type) {
        List<HealthCheck> checks = healthCheckRegistry.getByType(type);
        if (checks.isEmpty()) {
            return Uni.createFrom()
                    .item(HealthPayload.fromChecks(
                            List.of(CheckResult.down("check-registry", type, "No checks registered", Map.of()))));
        }

        List<Uni<CheckResult>> executions =
                checks.stream().map(HealthCheck::execute).toList();
        return Uni.join().all(executions).andCollectFailures().map(HealthPayload::fromChecks);
    }
}
