package com.github.kaivu.vertxweb.repositories;

import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import java.util.List;

@Singleton
public class ProductRepositoryImpl implements ProductRepository {
    @Override
    public Uni<JsonObject> findById(String productId) {
        if ("1".equals(productId)) {
            return Uni.createFrom().item(new JsonObject().put("productId", "1").put("name", "Widget"));
        }
        return Uni.createFrom().failure(new ServiceException("Product not found", 404));
    }

    @Override
    public Uni<List<JsonObject>> findAll() {
        List<JsonObject> products =
                List.of(new JsonObject().put("productId", "1").put("name", "Widget"));
        return Uni.createFrom().item(products);
    }
}
