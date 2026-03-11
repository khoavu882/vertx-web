package com.github.kaivu.vertxweb.observability.health;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;

/**
 * Default registry backed by a Guice {@code Multibinder<HealthCheck>} set.
 *
 * <p>Adding a new check requires only one line in {@code AppModule} — this class never changes:
 * <pre>{@code
 * healthChecks.addBinding().to(MyNewCheck.class).in(Singleton.class);
 * }</pre>
 */
@Singleton
public class DefaultHealthCheckRegistry implements HealthCheckRegistry {

    private final Set<HealthCheck> checks;

    @Inject
    public DefaultHealthCheckRegistry(Set<HealthCheck> checks) {
        this.checks = checks;
    }

    @Override
    public List<HealthCheck> getByType(CheckType type) {
        return checks.stream().filter(check -> check.type() == type).toList();
    }
}
