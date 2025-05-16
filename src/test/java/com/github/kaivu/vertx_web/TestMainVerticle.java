package com.github.kaivu.vertx_web;

import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class TestMainVerticle {

    //  @BeforeEach
    //  void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
    //    vertx.deployVerticle(new AppVerticle()).onComplete(testContext.succeeding(id -> testContext.completeNow()));
    //  }
    //
    //  @Test
    //  void verticle_deployed(Vertx vertx, VertxTestContext testContext) throws Throwable {
    //    testContext.completeNow();
    //  }
}
