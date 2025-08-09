package com.github.kaivu.vertxweb.services;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.context.ContextAwareVertxWrapper;
import com.github.kaivu.vertxweb.repositories.ProductRepository;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Khoa Vu.
 * Mail: kai.vu.dev@gmail.com
 * Date: 9/12/24
 * Time: 10:22 AM
 */
@Singleton
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private final ProductRepository productRepository;
    private final Vertx vertx;
    private final ApplicationConfig appConfig;

    @Inject
    public ProductService(ProductRepository productRepository, Vertx vertx, ApplicationConfig appConfig) {
        this.productRepository = productRepository;
        this.vertx = vertx;
        this.appConfig = appConfig;
    }

    public Uni<JsonObject> getProductById(String productId) {
        return getProductByIdWithContext(productId, null);
    }

    public Uni<JsonObject> getProductByIdWithContext(String productId, RoutingContext ctx) {
        if (productId == null || productId.isBlank()) {
            return Uni.createFrom()
                    .failure(new ServiceException("Product ID must not be empty", AppConstants.Status.BAD_REQUEST));
        }

        ContextAwareVertxWrapper wrapper = ctx != null ? (ContextAwareVertxWrapper) ctx.get("contextWrapper") : null;

        if (wrapper != null) {
            wrapper.logEvent("service_operation_start", "operation", "getProductById", "productId", productId);
        }

        Uni<JsonObject> result = productRepository.findById(productId);

        if (wrapper != null) {
            wrapper.logEvent("service_operation_completed", "operation", "getProductById", "productId", productId);
        }

        return result;
    }

    public Uni<JsonObject> getAllProducts() {
        return getAllProductsWithContext(null);
    }

    public Uni<JsonObject> getAllProductsWithContext(RoutingContext ctx) {
        ContextAwareVertxWrapper wrapper = ctx != null ? (ContextAwareVertxWrapper) ctx.get("contextWrapper") : null;

        if (wrapper != null) {
            wrapper.logEvent("service_operation_start", "operation", "getAllProducts");
        }

        log.info("Fetching all products...");

        Uni<JsonObject> result = performGetAllProducts();

        if (wrapper != null) {
            wrapper.logEvent("service_operation_completed", "operation", "getAllProducts");
        }

        return result;
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
                    log.error("Error fetching products", throwable);
                    return new ServiceException("Failed to fetch products", AppConstants.Status.INTERNAL_SERVER_ERROR);
                });
    }

    public Uni<JsonObject> createProduct(JsonObject product) {
        return createProductWithContext(product, null);
    }

    public Uni<JsonObject> createProductWithContext(JsonObject product, RoutingContext ctx) {
        if (product == null || product.isEmpty()) {
            return Uni.createFrom()
                    .failure(new ServiceException("Product data must not be empty", AppConstants.Status.BAD_REQUEST));
        }

        String name = product.getString("name");
        String category = product.getString("category");
        Double price = product.getDouble("price");

        if (name == null || name.isBlank()) {
            return Uni.createFrom()
                    .failure(new ServiceException("Product name is required", AppConstants.Status.BAD_REQUEST));
        }
        if (category == null || category.isBlank()) {
            return Uni.createFrom()
                    .failure(new ServiceException("Product category is required", AppConstants.Status.BAD_REQUEST));
        }
        if (price == null || price <= 0) {
            return Uni.createFrom()
                    .failure(new ServiceException(
                            "Product price must be greater than 0", AppConstants.Status.BAD_REQUEST));
        }

        ContextAwareVertxWrapper wrapper = ctx != null ? (ContextAwareVertxWrapper) ctx.get("contextWrapper") : null;

        if (wrapper != null) {
            wrapper.logEvent("service_operation_start", "operation", "createProduct", "productName", name);
        }

        log.info("Creating new product: {}", name);

        Uni<JsonObject> result = performCreateProduct(product);

        if (wrapper != null) {
            wrapper.logEvent("service_operation_completed", "operation", "createProduct", "productName", name);
        }

        return result;
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
                    log.error("Error creating product", throwable);
                    return new ServiceException("Failed to create product", AppConstants.Status.INTERNAL_SERVER_ERROR);
                });
    }

    public Uni<JsonObject> updateProductStock(String productId, int newQuantity) {
        return updateProductStockWithContext(productId, newQuantity, null);
    }

    public Uni<JsonObject> updateProductStockWithContext(String productId, int newQuantity, RoutingContext ctx) {
        if (productId == null || productId.isBlank()) {
            return Uni.createFrom()
                    .failure(new ServiceException("Product ID must not be empty", AppConstants.Status.BAD_REQUEST));
        }
        if (newQuantity < 0) {
            return Uni.createFrom()
                    .failure(new ServiceException("Quantity cannot be negative", AppConstants.Status.BAD_REQUEST));
        }

        ContextAwareVertxWrapper wrapper = ctx != null ? (ContextAwareVertxWrapper) ctx.get("contextWrapper") : null;

        if (wrapper != null) {
            wrapper.logEvent(
                    "service_operation_start",
                    "operation",
                    "updateProductStock",
                    "productId",
                    productId,
                    "newQuantity",
                    newQuantity);
        }

        log.info("Updating stock for product: {} to quantity: {}", productId, newQuantity);

        return getProductByIdWithContext(productId, ctx)
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
                    updatedProduct.put("updatedAt", java.time.Instant.now().toString());
                    return updatedProduct;
                })
                .onFailure()
                .transform(throwable -> {
                    if (throwable instanceof ServiceException) {
                        return throwable;
                    }
                    log.error("Error updating product stock: {}", productId, throwable);
                    return new ServiceException(
                            "Failed to update product stock", AppConstants.Status.INTERNAL_SERVER_ERROR);
                });
    }
}
