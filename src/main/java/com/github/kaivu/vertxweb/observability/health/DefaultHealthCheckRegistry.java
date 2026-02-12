package com.github.kaivu.vertxweb.observability.health;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class DefaultHealthCheckRegistry implements HealthCheckRegistry {
    private final List<HealthCheck> checks;

    @Inject
    public DefaultHealthCheckRegistry(
            ProcessLivenessCheck processLivenessCheck,
            EventBusReadinessCheck eventBusReadinessCheck,
            StartupCheck startupCheck) {
        this.checks = List.of(processLivenessCheck, eventBusReadinessCheck, startupCheck);
    }

    @Override
    public List<HealthCheck> getByType(CheckType type) {
        return checks.stream().filter(check -> check.type() == type).toList();
    }
}
