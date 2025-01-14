package com.github.kaivu.vertx_web.services;

import io.vertx.core.Vertx;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

/**
 * Created by Khoa Vu.
 * Mail: khoavd12@fpt.com
 * Date: 9/12/24
 * Time: 10:22 AM
 */

public class ProductService {
  private final Vertx vertx;

  public ProductService(Vertx vertx) {
    this.vertx = vertx;
  }

  public Uni<JsonObject> getAllProducts() {
    // Simulate async operation
    return Uni.createFrom().item(new JsonObject().put("productId", 1).put("name", "Widget")); // Example JSON response
  }

  public Uni<JsonObject> getProductById(String productId) {
    // Simulate async operation
    return Uni.createFrom().item(new JsonObject().put("productId", productId).put("name", "Widget")); // Example JSON response
  }
}

