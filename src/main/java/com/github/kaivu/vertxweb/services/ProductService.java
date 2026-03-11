package com.github.kaivu.vertxweb.services;

import com.github.kaivu.vertxweb.constants.HttpStatusCodes;
import com.github.kaivu.vertxweb.context.CorrelationContext;
import com.github.kaivu.vertxweb.domain.Product;
import com.github.kaivu.vertxweb.patterns.CircuitBreakerRegistry;
import com.github.kaivu.vertxweb.repositories.ProductRepository;
import com.github.kaivu.vertxweb.repositories.exception.RepositoryConnectionException;
import com.github.kaivu.vertxweb.repositories.exception.RepositoryNotFoundException;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Khoa Vu.
 * Mail: kai.vu.dev@gmail.com
 * Date: 9/12/24
 * Time: 10:22 AM
 */
@Singleton
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private final ProductRepository productRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Inject
    public ProductService(ProductRepository productRepository, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.productRepository = productRepository;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    public Uni<JsonObject> getProductById(String productId) {
        return getProductByIdWithContext(productId, null);
    }

    public Uni<JsonObject> getProductByIdWithContext(String productId, CorrelationContext correlation) {
        if (productId == null || productId.isBlank()) {
            return Uni.createFrom()
                    .failure(new ServiceException("Product ID must not be empty", HttpStatusCodes.BAD_REQUEST));
        }

        if (correlation != null) {
            correlation.logEvent(log, "service_start", "operation", "getProductById", "productId", productId);
        }

        return circuitBreakerRegistry
                .getDatabaseCircuitBreaker()
                .execute(() -> performGetProductById(productId))
                .onItem()
                .invoke(result -> {
                    if (correlation != null) {
                        correlation.logEvent(
                                log, "service_completed", "operation", "getProductById", "productId", productId);
                    }
                });
    }

    public Uni<JsonObject> getAllProducts() {
        return getAllProductsWithContext(null);
    }

    public Uni<JsonObject> getAllProductsWithContext(CorrelationContext correlation) {
        if (correlation != null) {
            correlation.logEvent(log, "service_start", "operation", "getAllProducts");
        }

        log.info("Fetching all products...");

        return circuitBreakerRegistry
                .getDatabaseCircuitBreaker()
                .execute(this::performGetAllProducts)
                .onItem()
                .invoke(result -> {
                    if (correlation != null) {
                        correlation.logEvent(log, "service_completed", "operation", "getAllProducts");
                    }
                });
    }

    private Uni<JsonObject> performGetAllProducts() {
        return productRepository
                .findAll()
                .onItem()
                .transform(productList -> {
                    JsonArray products = new JsonArray();
                    productList.stream().map(Product::toJsonObject).forEach(products::add);
                    return new JsonObject()
                            .put("products", products)
                            .put("total", products.size())
                            .put("timestamp", System.currentTimeMillis());
                })
                .onFailure()
                .transform(this::mapRepositoryException);
    }

    private Uni<JsonObject> performGetProductById(String productId) {
        return productRepository
                .findById(productId)
                .onItem()
                .transform(Product::toJsonObject)
                .onFailure()
                .transform(this::mapRepositoryException);
    }

    public Uni<JsonObject> createProduct(JsonObject product) {
        return createProductWithContext(product, null);
    }

    public Uni<JsonObject> createProductWithContext(JsonObject product, CorrelationContext correlation) {
        if (product == null || product.isEmpty()) {
            return Uni.createFrom()
                    .failure(new ServiceException("Product data must not be empty", HttpStatusCodes.BAD_REQUEST));
        }

        String name = product.getString("name");
        String category = product.getString("category");
        Double price = product.getDouble("price");

        if (name == null || name.isBlank()) {
            return Uni.createFrom()
                    .failure(new ServiceException("Product name is required", HttpStatusCodes.BAD_REQUEST));
        }
        if (category == null || category.isBlank()) {
            return Uni.createFrom()
                    .failure(new ServiceException("Product category is required", HttpStatusCodes.BAD_REQUEST));
        }
        if (price == null || price <= 0) {
            return Uni.createFrom()
                    .failure(new ServiceException("Product price must be greater than 0", HttpStatusCodes.BAD_REQUEST));
        }

        if (correlation != null) {
            correlation.logEvent(log, "service_start", "operation", "createProduct", "productName", name);
        }

        log.info("Creating new product: {}", name);

        return circuitBreakerRegistry
                .getDatabaseCircuitBreaker()
                .execute(() -> performCreateProduct(product))
                .onItem()
                .invoke(result -> {
                    if (correlation != null) {
                        correlation.logEvent(
                                log, "service_completed", "operation", "createProduct", "productName", name);
                    }
                });
    }

    private Uni<JsonObject> performCreateProduct(JsonObject data) {
        Product newProduct = Product.forCreate(
                data.getString("name"),
                data.getString("category"),
                BigDecimal.valueOf(data.getDouble("price")),
                data.getInteger("quantity", 0),
                data.getBoolean("inStock", true));

        return productRepository
                .save(newProduct)
                .onItem()
                .transform(saved ->
                        saved.toJsonObject().put("createdAt", Instant.now().toString()))
                .onFailure()
                .transform(this::mapRepositoryException);
    }

    public Uni<JsonObject> updateProductStock(String productId, int newQuantity) {
        return updateProductStockWithContext(productId, newQuantity, null);
    }

    public Uni<JsonObject> updateProductStockWithContext(
            String productId, int newQuantity, CorrelationContext correlation) {
        if (productId == null || productId.isBlank()) {
            return Uni.createFrom()
                    .failure(new ServiceException("Product ID must not be empty", HttpStatusCodes.BAD_REQUEST));
        }
        if (newQuantity < 0) {
            return Uni.createFrom()
                    .failure(new ServiceException("Quantity cannot be negative", HttpStatusCodes.BAD_REQUEST));
        }

        if (correlation != null) {
            correlation.logEvent(
                    log,
                    "service_start",
                    "operation",
                    "updateProductStock",
                    "productId",
                    productId,
                    "newQuantity",
                    newQuantity);
        }

        log.info("Updating stock for product: {} to quantity: {}", productId, newQuantity);

        return circuitBreakerRegistry
                .getDatabaseCircuitBreaker()
                .execute(() -> performUpdateProductStock(productId, newQuantity))
                .onItem()
                .invoke(result -> {
                    if (correlation != null) {
                        correlation.logEvent(
                                log, "service_completed", "operation", "updateProductStock", "productId", productId);
                    }
                });
    }

    private Uni<JsonObject> performUpdateProductStock(String productId, int newQuantity) {
        return productRepository
                .updateStock(productId, newQuantity)
                .onItem()
                .transform(updated ->
                        updated.toJsonObject().put("updatedAt", Instant.now().toString()))
                .onFailure()
                .transform(this::mapRepositoryException);
    }

    /**
     * Maps repository exceptions to service exceptions with appropriate HTTP status codes.
     *
     * <p>Mapping:
     * <ul>
     *   <li>{@link RepositoryNotFoundException} → 404 (circuit breaker does NOT count this)
     *   <li>{@link RepositoryConnectionException} → 503 (circuit breaker counts this)
     *   <li>{@link ServiceException} → passed through as-is
     *   <li>Other → 500 (circuit breaker counts this)
     * </ul>
     */
    private Throwable mapRepositoryException(Throwable throwable) {
        if (throwable instanceof ServiceException) {
            return throwable;
        }
        if (throwable instanceof RepositoryNotFoundException) {
            return new ServiceException(throwable.getMessage(), HttpStatusCodes.NOT_FOUND);
        }
        if (throwable instanceof RepositoryConnectionException) {
            return new ServiceException("Service temporarily unavailable", HttpStatusCodes.SERVICE_UNAVAILABLE);
        }
        log.error("Unexpected repository error", throwable);
        return new ServiceException("Internal error", HttpStatusCodes.INTERNAL_SERVER_ERROR);
    }
}
