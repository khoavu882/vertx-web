package com.github.kaivu.vertxweb.repositories;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import java.util.List;

public interface ProductRepository {
    Uni<JsonObject> findById(String productId);

    Uni<List<JsonObject>> findAll();
}
