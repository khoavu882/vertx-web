package com.github.kaivu.vertxweb.services;

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
    private final Vertx vertx;

    @Inject
    public ProductService(Vertx vertx) {
        this.vertx = vertx;
    }

    public Uni<JsonObject> getAllProducts() {
        log.info("Fetching all products...");

        return Uni.createFrom()
                .item(0)
                .onItem()
                .delayIt()
                .by(Duration.ofMillis(150 + ThreadLocalRandom.current().nextInt(300)))
                .onItem()
                .transform(ignored -> {
                    JsonArray products = new JsonArray()
                            .add(new JsonObject()
                                    .put("id", 1)
                                    .put("name", "Laptop Pro")
                                    .put("category", "Electronics")
                                    .put("price", 1299.99)
                                    .put("inStock", true)
                                    .put("quantity", 45))
                            .add(new JsonObject()
                                    .put("id", 2)
                                    .put("name", "Wireless Mouse")
                                    .put("category", "Accessories")
                                    .put("price", 29.99)
                                    .put("inStock", true)
                                    .put("quantity", 120))
                            .add(new JsonObject()
                                    .put("id", 3)
                                    .put("name", "USB-C Hub")
                                    .put("category", "Accessories")
                                    .put("price", 49.99)
                                    .put("inStock", false)
                                    .put("quantity", 0));

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

    public Uni<JsonObject> getProductById(String productId) {
        if (productId == null || productId.isBlank()) {
            return Uni.createFrom().failure(new ServiceException("Product ID must not be empty", 400));
        }

        log.info("Fetching product by ID: {}", productId);

        return Uni.createFrom()
                .item(productId)
                .onItem()
                .delayIt()
                .by(Duration.ofMillis(75 + ThreadLocalRandom.current().nextInt(125)))
                .onItem()
                .transform(id -> {
                    // Simulate product database lookup
                    return switch (id) {
                        case "1" -> new JsonObject()
                                .put("id", 1)
                                .put("name", "Laptop Pro")
                                .put("category", "Electronics")
                                .put("price", 1299.99)
                                .put("description", "High-performance laptop with 16GB RAM and 512GB SSD")
                                .put("inStock", true)
                                .put("quantity", 45)
                                .put("createdAt", "2024-01-01T10:00:00Z");
                        case "2" -> new JsonObject()
                                .put("id", 2)
                                .put("name", "Wireless Mouse")
                                .put("category", "Accessories")
                                .put("price", 29.99)
                                .put("description", "Ergonomic wireless mouse with precision tracking")
                                .put("inStock", true)
                                .put("quantity", 120)
                                .put("createdAt", "2024-01-15T14:30:00Z");
                        case "3" -> new JsonObject()
                                .put("id", 3)
                                .put("name", "USB-C Hub")
                                .put("category", "Accessories")
                                .put("price", 49.99)
                                .put("description", "Multi-port USB-C hub with HDMI and USB 3.0 ports")
                                .put("inStock", false)
                                .put("quantity", 0)
                                .put("createdAt", "2024-02-01T09:15:00Z");
                        default -> throw new ServiceException("Product not found", 404);
                    };
                })
                .onFailure(ServiceException.class)
                .recoverWithUni(failure -> Uni.createFrom().failure(failure))
                .onFailure()
                .transform(throwable -> {
                    log.error("Error fetching product: {}", productId, throwable);
                    return new ServiceException("Failed to fetch product", 500);
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
