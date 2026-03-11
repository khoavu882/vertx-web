package com.github.kaivu.vertxweb.repositories;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.SqlConnection;
import java.util.function.Function;

/**
 * Guice-injectable wrapper for multi-statement database transactions.
 *
 * <p>Inject this into any service that needs to coordinate multiple repository
 * operations atomically. The provided {@code Function} receives a live
 * {@link SqlConnection} and must return a {@link Uni} — commit and rollback
 * are handled automatically by the pool.
 *
 * <pre>{@code
 * transactionTemplate.execute(conn ->
 *     repo.saveWithConnection(conn, product)
 *         .chain(saved -> auditRepo.recordWithConnection(conn, saved))
 * ).subscribe()...
 * }</pre>
 */
@Singleton
public class TransactionTemplate {

    private final MySQLPool pool;

    @Inject
    public TransactionTemplate(MySQLPool pool) {
        this.pool = pool;
    }

    public <T> Uni<T> execute(Function<SqlConnection, Uni<T>> work) {
        return Uni.createFrom()
                .completionStage(pool.withTransaction(conn ->
                                Future.fromCompletionStage(work.apply(conn).subscribeAsCompletionStage()))
                        .toCompletionStage());
    }
}
