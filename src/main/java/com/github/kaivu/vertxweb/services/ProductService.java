package com.github.kaivu.vertxweb.services;

import com.github.kaivu.vertxweb.repositories.ProductRepository;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Khoa Vu.
 * Mail: khoavd12@fpt.com
 * Date: 9/12/24
 * Time: 10:22 AM
 */
@Singleton
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private final ProductRepository productRepository;
    private final Vertx vertx;

    @Inject
    public ProductService(ProductRepository productRepository, Vertx vertx) {
        this.productRepository = productRepository;
        this.vertx = vertx;
    }

    public Uni<JsonObject> getProductById(String productId) {
        if (productId == null || productId.isBlank()) {
            return Uni.createFrom().failure(new ServiceException("Product ID must not be empty", 400));
        }
        return productRepository.findById(productId);
    }

    public Uni<JsonObject> getAllProducts() {
        log.info("Fetching all products...");
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
                    return new ServiceException("Failed to fetch products", 500);
                });
    }

    public Uni<JsonObject> createProduct(JsonObject product) {
        if (product == null || product.isEmpty()) {
            return Uni.createFrom().failure(new ServiceException("Product data must not be empty", 400));
        }

        String name = product.getString("name");
        String category = product.getString("category");
        Double price = product.getDouble("price");

        if (name == null || name.isBlank()) {
            return Uni.createFrom().failure(new ServiceException("Product name is required", 400));
        }
        if (category == null || category.isBlank()) {
            return Uni.createFrom().failure(new ServiceException("Product category is required", 400));
        }
        if (price == null || price <= 0) {
            return Uni.createFrom().failure(new ServiceException("Product price must be greater than 0", 400));
        }

        log.info("Creating new product: {}", name);

        return Uni.createFrom()
                .item(product)
                .onItem()
                .delayIt()
                .by(Duration.ofMillis(300 + ThreadLocalRandom.current().nextInt(400)))
                .onItem()
                .transform(productData -> {
                    // Simulate database insert with generated ID
                    int newId = ThreadLocalRandom.current().nextInt(1000, 9999);
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
                    return new ServiceException("Failed to create product", 500);
                });
    }

    public Uni<JsonObject> updateProductStock(String productId, int newQuantity) {
        if (productId == null || productId.isBlank()) {
            return Uni.createFrom().failure(new ServiceException("Product ID must not be empty", 400));
        }
        if (newQuantity < 0) {
            return Uni.createFrom().failure(new ServiceException("Quantity cannot be negative", 400));
        }

        log.info("Updating stock for product: {} to quantity: {}", productId, newQuantity);

        return getProductById(productId)
                .onItem()
                .delayIt()
                .by(Duration.ofMillis(100 + ThreadLocalRandom.current().nextInt(100)))
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
                    return new ServiceException("Failed to update product stock", 500);
                });
    }
}
