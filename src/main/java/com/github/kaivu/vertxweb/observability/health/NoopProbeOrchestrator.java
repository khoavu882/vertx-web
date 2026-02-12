package com.github.kaivu.vertxweb.observability.health;

import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import java.util.List;

/**
 * Phase 1 foundation implementation.
 *
 * Keeps health abstraction wiring available without changing current HealthRouter behavior.
 */
@Singleton
public class NoopProbeOrchestrator implements ProbeOrchestrator {
    @Override
    public Uni<HealthPayload> live() {
        return Uni.createFrom().item(HealthPayload.fromChecks(List.of()));
    }

    @Override
    public Uni<HealthPayload> ready() {
        return Uni.createFrom().item(HealthPayload.fromChecks(List.of()));
    }

    @Override
    public Uni<HealthPayload> started() {
        return Uni.createFrom().item(HealthPayload.fromChecks(List.of()));
    }

    @Override
    public Uni<HealthPayload> overall() {
        return Uni.createFrom().item(HealthPayload.fromChecks(List.of()));
    }
}
