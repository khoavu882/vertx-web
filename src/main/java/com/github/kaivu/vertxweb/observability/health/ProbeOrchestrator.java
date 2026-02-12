package com.github.kaivu.vertxweb.observability.health;

import io.smallrye.mutiny.Uni;

public interface ProbeOrchestrator {
    Uni<HealthPayload> live();

    Uni<HealthPayload> ready();

    Uni<HealthPayload> started();

    Uni<HealthPayload> overall();
}
