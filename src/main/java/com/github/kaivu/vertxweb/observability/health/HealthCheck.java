package com.github.kaivu.vertxweb.observability.health;

import io.smallrye.mutiny.Uni;

public interface HealthCheck {
    String name();

    CheckType type();

    Uni<CheckResult> execute();
}
