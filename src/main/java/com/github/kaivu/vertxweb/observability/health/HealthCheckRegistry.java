package com.github.kaivu.vertxweb.observability.health;

import java.util.List;

public interface HealthCheckRegistry {
    List<HealthCheck> getByType(CheckType type);
}
