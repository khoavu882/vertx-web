package com.github.kaivu.vertxweb.services;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.HttpStatusCodes;
import com.github.kaivu.vertxweb.context.CorrelationContext;
import com.github.kaivu.vertxweb.patterns.CircuitBreakerRegistry;
import com.github.kaivu.vertxweb.repositories.ProductRepository;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
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
    private final ApplicationConfig appConfig;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Inject
    public ProductService(
            ProductRepository productRepository,
            ApplicationConfig appConfig,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.productRepository = productRepository;
        this.appConfig = appConfig;
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
                    productList.forEach(products::add);
                    return new JsonObject()
                            .put("products", products)
                            .put("total", products.size())
                            .put("timestamp", System.currentTimeMillis());
                })
                .onFailure()
                .transform(throwable -> {
                    if (throwable instanceof ServiceException) {
                        return throwable;
                    }
                    log.error("Error fetching products", throwable);
                    return new ServiceException("Failed to fetch products", HttpStatusCodes.INTERNAL_SERVER_ERROR);
                });
    }

    private Uni<JsonObject> performGetProductById(String productId) {
        return productRepository.findById(productId).onFailure().transform(throwable -> {
            if (throwable instanceof ServiceException) {
                return throwable;
            }
            log.error("Error fetching product by ID: {}", productId, throwable);
            return new ServiceException("Failed to fetch product", HttpStatusCodes.INTERNAL_SERVER_ERROR);
        });
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

    private Uni<JsonObject> performCreateProduct(JsonObject product) {
        return Uni.createFrom()
                .item(product)
                .onItem()
                .delayIt()
                .by(Duration.ofMillis(appConfig.service().productCreateBaseDelayMs()
                        + ThreadLocalRandom.current()
                                .nextInt(appConfig.service().productCreateMaxVarianceMs())))
                .onItem()
                .transform(productData -> {
                    // Simulate database insert with generated ID
                    int newId = ThreadLocalRandom.current()
                            .nextInt(
                                    appConfig.service().minIdRange(),
                                    appConfig.service().maxIdRange());
                    return new JsonObject()
                            .put("id", newId)
                            .put("name", productData.getString("name"))
                            .put("category", productData.getString("category"))
                            .put("price", productData.getDouble("price"))
                            .put("description", productData.getString("description", ""))
                            .put("inStock", true)
                            .put("quantity", productData.getInteger("quantity", 0))
                            .put("createdAt", java.time.Instant.now().toString());
                })
                .onFailure()
                .transform(throwable -> {
                    if (throwable instanceof ServiceException) {
                        return throwable;
                    }
                    log.error("Error creating product", throwable);
                    return new ServiceException("Failed to create product", HttpStatusCodes.INTERNAL_SERVER_ERROR);
                });
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
                .execute(() -> performGetProductById(productId)
                        .onItem()
                        .delayIt()
                        .by(Duration.ofMillis(appConfig.service().baseDelayMs()
                                + ThreadLocalRandom.current()
                                        .nextInt(appConfig.service().baseDelayMs())))
                        .onItem()
                        .transform(existingProduct -> {
                            JsonObject updatedProduct = existingProduct.copy();
                            updatedProduct.put("quantity", newQuantity);
                            updatedProduct.put("inStock", newQuantity > 0);
                            updatedProduct.put(
                                    "updatedAt", java.time.Instant.now().toString());
                            return updatedProduct;
                        })
                        .onFailure()
                        .transform(throwable -> {
                            if (throwable instanceof ServiceException) {
                                return throwable;
                            }
                            log.error("Error updating product stock: {}", productId, throwable);
                            return new ServiceException(
                                    "Failed to update product stock", HttpStatusCodes.INTERNAL_SERVER_ERROR);
                        }))
                .onItem()
                .invoke(result -> {
                    if (correlation != null) {
                        correlation.logEvent(
                                log, "service_completed", "operation", "updateProductStock", "productId", productId);
                    }
                });
    }
}
