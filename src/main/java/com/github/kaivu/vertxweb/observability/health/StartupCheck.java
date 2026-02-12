package com.github.kaivu.vertxweb.observability.health;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import java.util.Map;

@Singleton
public class StartupCheck implements HealthCheck {
    private final ApplicationState applicationState;

    @Inject
    public StartupCheck(ApplicationState applicationState) {
        this.applicationState = applicationState;
    }

    @Override
    public String name() {
        return "startup";
    }

    @Override
    public CheckType type() {
        return CheckType.STARTUP;
    }

    @Override
    public Uni<CheckResult> execute() {
        return Uni.createFrom().item(CheckResult.up(name(), type(), Map.of("uptimeMs", applicationState.uptimeMs())));
    }
}
