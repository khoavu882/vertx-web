package com.github.kaivu.vertx_web.services;

import io.vertx.core.Vertx;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

/**
 * Created by Khoa Vu.
 * Mail: khoavd12@fpt.com
 * Date: 9/12/24
 * Time: 10:25 AM
 */

public class UserService {
  private final Vertx vertx;

  public UserService(Vertx vertx) {
    this.vertx = vertx;
  }

  public Uni<JsonObject> getAllUsers() {
    // Simulate async operation
    return Uni.createFrom().item(new JsonObject().put("users", new JsonObject().put("id", 1).put("name", "John Doe"))); // Example JSON response
  }

  public Uni<JsonObject> getUserById(String userId) {
    // Simulate async operation
    return Uni.createFrom().item(new JsonObject().put("id", userId).put("name", "John Doe")); // Example JSON response
  }

  public Uni<JsonObject> createUser(JsonObject user) {
    // Simulate async operation
    return Uni.createFrom().item(user);
  }

  public Uni<JsonObject> updateUser(String userId, JsonObject user) {
    // Simulate async operation
    return Uni.createFrom().item(new JsonObject().put("id", userId).put("name", user.getValue("name")));
  }

  public Uni<String> deleteUser(String userId) {
    // Simulate async operation
    return Uni.createFrom().item("User " + userId + " deleted");
  }
}

