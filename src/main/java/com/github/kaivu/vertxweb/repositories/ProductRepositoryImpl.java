package com.github.kaivu.vertxweb.repositories;

import com.github.kaivu.vertxweb.domain.Product;
import com.github.kaivu.vertxweb.repositories.exception.RepositoryConnectionException;
import com.github.kaivu.vertxweb.repositories.exception.RepositoryException;
import com.github.kaivu.vertxweb.repositories.exception.RepositoryNotFoundException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MariaDB-backed implementation of {@link ProductRepository}.
 *
 * <p>All operations execute on the Vert.x event loop via {@link MySQLPool} — no JDBC,
 * no blocking. Future-to-Uni conversion uses {@code toCompletionStage()} consistently
 * with the existing codebase pattern (see {@code EventBusReadinessCheck}).
 *
 * <p>Error mapping: infrastructure failures become {@link RepositoryConnectionException};
 * missing rows become {@link RepositoryNotFoundException}. The service layer maps these
 * to the appropriate HTTP status codes — no HTTP knowledge lives here.
 */
@Singleton
public class ProductRepositoryImpl implements ProductRepository {

    private static final String SELECT_COLUMNS = "product_id, name, category, price, quantity, in_stock, created_at";
    private static final String FIND_BY_ID = "SELECT " + SELECT_COLUMNS + " FROM tbl_product WHERE product_id = ?";
    private static final String FIND_ALL = "SELECT " + SELECT_COLUMNS + " FROM tbl_product";
    private static final String INSERT =
            "INSERT INTO tbl_product (product_id, name, category, price, quantity, in_stock)"
                    + " VALUES (?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_STOCK = "UPDATE tbl_product SET quantity = ?, in_stock = ? WHERE product_id = ?";
    private static final String DELETE = "DELETE FROM tbl_product WHERE product_id = ?";

    private final MySQLPool pool;

    @Inject
    public ProductRepositoryImpl(MySQLPool pool) {
        this.pool = pool;
    }

    @Override
    public Uni<Product> findById(String productId) {
        return Uni.createFrom()
                .completionStage(pool.preparedQuery(FIND_BY_ID)
                        .execute(Tuple.of(productId))
                        .toCompletionStage())
                .onItem()
                .transformToUni(rows -> {
                    if (!rows.iterator().hasNext()) {
                        return Uni.createFrom()
                                .failure(new RepositoryNotFoundException("Product not found: " + productId));
                    }
                    return Uni.createFrom().item(rowToProduct(rows.iterator().next()));
                })
                .onFailure()
                .transform(this::mapException);
    }

    @Override
    public Uni<List<Product>> findAll() {
        return Uni.createFrom()
                .completionStage(pool.query(FIND_ALL).execute().toCompletionStage())
                .onItem()
                .transform(rows -> {
                    List<Product> products = new ArrayList<>();
                    rows.forEach(row -> products.add(rowToProduct(row)));
                    return products;
                })
                .onFailure()
                .transform(this::mapException);
    }

    @Override
    public Uni<Product> save(Product product) {
        String newId = UUID.randomUUID().toString();
        return Uni.createFrom()
                .completionStage(pool.preparedQuery(INSERT)
                        .execute(Tuple.of(
                                newId,
                                product.name(),
                                product.category(),
                                product.price(),
                                product.quantity(),
                                product.inStock()))
                        .toCompletionStage())
                .onItem()
                .transformToUni(ignored -> findById(newId))
                .onFailure()
                .transform(this::mapException);
    }

    @Override
    public Uni<Product> updateStock(String productId, int quantity) {
        boolean inStock = quantity > 0;
        return Uni.createFrom()
                .completionStage(pool.preparedQuery(UPDATE_STOCK)
                        .execute(Tuple.of(quantity, inStock, productId))
                        .toCompletionStage())
                .onItem()
                .transformToUni(rows -> {
                    if (rows.rowCount() == 0) {
                        return Uni.createFrom()
                                .failure(new RepositoryNotFoundException("Product not found: " + productId));
                    }
                    return findById(productId);
                })
                .onFailure()
                .transform(this::mapException);
    }

    @Override
    public Uni<Boolean> delete(String productId) {
        return Uni.createFrom()
                .completionStage(
                        pool.preparedQuery(DELETE).execute(Tuple.of(productId)).toCompletionStage())
                .onItem()
                .transform(rows -> {
                    if (rows.rowCount() == 0) {
                        throw new RepositoryNotFoundException("Product not found: " + productId);
                    }
                    return true;
                })
                .onFailure()
                .transform(this::mapException);
    }

    private Product rowToProduct(Row row) {
        return new Product(
                row.getString("product_id"),
                row.getString("name"),
                row.getString("category"),
                row.getBigDecimal("price") != null ? row.getBigDecimal("price") : BigDecimal.ZERO,
                row.getInteger("quantity"),
                Boolean.TRUE.equals(row.getBoolean("in_stock")),
                row.getLocalDateTime("created_at"));
    }

    /**
     * Maps any throwable to the appropriate {@link RepositoryException} subclass.
     * Already-mapped exceptions are returned as-is to prevent double-wrapping.
     */
    private Throwable mapException(Throwable t) {
        if (t instanceof RepositoryException) {
            return t;
        }
        String msg = t.getMessage() != null ? t.getMessage() : "Database error";
        return new RepositoryConnectionException("Database connection error: " + msg, t);
    }
}
