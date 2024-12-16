package com.github.kaivu.vertx_web;

import com.github.kaivu.vertx_web.web.routes.RouterConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.Router;

public class AppVerticle extends AbstractVerticle {
  private static final Logger log = LoggerFactory.getLogger(AppVerticle.class);

  public static void main(String[] args) {
    Vertx.vertx().deployVerticle(new AppVerticle());
  }

  @Override
  public void start(Promise<Void> startPromise) {
    RouterConfig routerConfig = new RouterConfig(vertx);
    Router router = routerConfig.getRouter();

    vertx.createHttpServer().requestHandler(router).listen(8080, result -> {
      if (result.succeeded()) {
        log.info("HTTP server started on port 8080");
        startPromise.complete();
      } else {
        log.error("Failed to start HTTP server", result.cause());
        startPromise.fail(result.cause());
      }
    });
  }

  @Override
  public void stop() {
    log.info("Shutting down application");
  }
}
