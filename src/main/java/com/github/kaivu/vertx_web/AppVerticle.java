package com.github.kaivu.vertx_web;

import com.github.kaivu.vertx_web.config.AppModule;
import com.github.kaivu.vertx_web.web.routes.RouterConfig;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(AppVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) {
        Injector injector = Guice.createInjector(new AppModule(vertx));
        RouterConfig routerConfig = injector.getInstance(RouterConfig.class);

        int port = config().getInteger("http.port", 8080);
        vertx.createHttpServer().requestHandler(routerConfig.getRouter()).listen(port, http -> {
            if (http.succeeded()) {
                startPromise.complete();
                log.info("HTTP server started on port {}", port);
            } else {
                startPromise.fail(http.cause());
            }
        });
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        // Deploy a worker verticle for handling blocking operations
        DeploymentOptions workerOptions =
                new DeploymentOptions().setWorker(true).setWorkerPoolSize(10).setWorkerPoolName("app-worker-pool");

        vertx.deployVerticle(new WorkerVerticle(), workerOptions, res -> {
            if (res.succeeded()) {
                log.info("Worker verticle deployed successfully");
            } else {
                log.info("Failed to deploy worker verticle: {}", res.cause().getMessage());
            }
        });

        vertx.deployVerticle(new AppVerticle());
    }
}
