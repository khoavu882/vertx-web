package com.github.kaivu.vertxweb.services;

import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Vertx;

/**
 * Created by Khoa Vu.
 * Mail: khoavd12@fpt.com
 * Date: 9/12/24
 * Time: 10:22 AM
 */
@Singleton
public class ProductService {
    private final Vertx vertx;

    @Inject
    public ProductService(Vertx vertx) {
        this.vertx = vertx;
    }

    public Uni<JsonObject> getAllProducts() {
        // Simulate async operation
        return Uni.createFrom()
                .item(new JsonObject().put("productId", 1).put("name", "Widget")); // Example JSON response
    }

    public Uni<JsonObject> getProductById(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new ServiceException("Product ID must not be empty", 400);
        }
        if (!"1".equals(productId)) {
            throw new ServiceException("Product not found", 404);
        }
        return Uni.createFrom()
                .item(new JsonObject().put("productId", productId).put("name", "Widget"));
    }
}
