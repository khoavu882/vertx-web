package com.github.kaivu.vertxweb.observability.health;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.mysqlclient.MySQLPool;
import java.util.Map;

/**
 * Readiness check that pings the database with {@code SELECT 1}.
 *
 * <p>Registered via {@code Multibinder<HealthCheck>} in {@code AppModule} — no changes to
 * {@link DefaultHealthCheckRegistry} are needed when adding further checks.
 */
@Singleton
public class DatabaseReadinessCheck implements HealthCheck {

    private final MySQLPool pool;

    @Inject
    public DatabaseReadinessCheck(MySQLPool pool) {
        this.pool = pool;
    }

    @Override
    public String name() {
        return "database";
    }

    @Override
    public CheckType type() {
        return CheckType.READINESS;
    }

    @Override
    public Uni<CheckResult> execute() {
        long startedAt = System.currentTimeMillis();
        return Uni.createFrom()
                .completionStage(pool.query("SELECT 1").execute().toCompletionStage())
                .onItem()
                .transform(rows -> {
                    long durationMs = System.currentTimeMillis() - startedAt;
                    return CheckResult.up(name(), type(), Map.of("responseTimeMs", durationMs));
                })
                .onFailure()
                .recoverWithItem(error -> {
                    long durationMs = System.currentTimeMillis() - startedAt;
                    String msg = error.getMessage() != null ? error.getMessage() : "database ping failed";
                    return CheckResult.down(name(), type(), msg, Map.of("responseTimeMs", durationMs));
                });
    }
}
