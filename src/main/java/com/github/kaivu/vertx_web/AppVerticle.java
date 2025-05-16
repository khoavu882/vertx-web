package com.github.kaivu.vertx_web;

import com.github.kaivu.vertx_web.config.AppModule;
import com.github.kaivu.vertx_web.web.routes.RouterConfig;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class AppVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        // Create Guice injector
        Injector injector = Guice.createInjector(new AppModule(vertx));

        // Get router configuration
        RouterConfig routerConfig = injector.getInstance(RouterConfig.class);

        // Start HTTP server
        int port = config().getInteger("http.port", 8080);
        vertx.createHttpServer().requestHandler(routerConfig.getRouter()).listen(port, http -> {
            if (http.succeeded()) {
                startPromise.complete();
                System.out.println("HTTP server started on port " + port);
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
                System.out.println("Worker verticle deployed successfully");
            } else {
                System.err.println("Failed to deploy worker verticle: " + res.cause());
            }
        });

        // Deploy the main verticle
        vertx.deployVerticle(new AppVerticle());
    }
}
