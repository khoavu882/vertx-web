package com.github.kaivu.vertxweb.repositories;

import com.github.kaivu.vertxweb.constants.HttpStatusCodes;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;

/**
 * Stub implementation of ProductRepository.
 *
 * This class provides in-memory placeholder data so the application starts and routes
 * function correctly without a real database. Replace this implementation with a
 * proper database-backed repository (e.g., JOOQ or Spring Data) when integrating
 * persistent storage.
 */
@Singleton
public class ProductRepositoryImpl implements ProductRepository {

    @Inject
    public ProductRepositoryImpl() {}

    private static final Map<String, JsonObject> PRODUCTS = Map.of(
            "1",
            new JsonObject()
                    .put("productId", "1")
                    .put("name", "Widget")
                    .put("category", "Hardware")
                    .put("price", 9.99)
                    .put("quantity", 100)
                    .put("inStock", true),
            "2",
            new JsonObject()
                    .put("productId", "2")
                    .put("name", "Gadget")
                    .put("category", "Electronics")
                    .put("price", 49.99)
                    .put("quantity", 25)
                    .put("inStock", true),
            "3",
            new JsonObject()
                    .put("productId", "3")
                    .put("name", "Doohickey")
                    .put("category", "Accessories")
                    .put("price", 4.99)
                    .put("quantity", 0)
                    .put("inStock", false));

    @Override
    public Uni<JsonObject> findById(String productId) {
        JsonObject product = PRODUCTS.get(productId);
        if (product != null) {
            return Uni.createFrom().item(product.copy());
        }
        return Uni.createFrom().failure(new ServiceException("Product not found", HttpStatusCodes.NOT_FOUND));
    }

    @Override
    public Uni<List<JsonObject>> findAll() {
        List<JsonObject> products =
                PRODUCTS.values().stream().map(JsonObject::copy).toList();
        return Uni.createFrom().item(products);
    }
}
