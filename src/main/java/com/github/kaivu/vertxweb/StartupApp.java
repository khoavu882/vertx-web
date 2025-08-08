package com.github.kaivu.vertxweb;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.config.ConfigProvider;
import com.github.kaivu.vertxweb.verticles.AppVerticle;
import com.github.kaivu.vertxweb.verticles.WorkerVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

/**
 * Created by Khoa Vu.
 * Mail: khoavd12@fpt.com
 * Date: 8/1/25
 * Time: 1:23 AM
 */
public class StartupApp {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StartupApp.class);

    public static void main(String[] args) {
        // Load application configuration
        ApplicationConfig config = ConfigProvider.createConfig();

        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new AppVerticle(), res -> {
            if (res.succeeded()) {
                log.info("AppVerticle deployed successfully");
            } else {
                log.info("Failed to deploy AppVerticle: {}", res.cause().getMessage());
            }
        });

        // Deploy worker verticle with configured pool size
        DeploymentOptions workerOptions = new DeploymentOptions()
                .setWorkerPoolSize(config.worker().poolSize())
                .setMaxWorkerExecuteTime(config.worker().maxExecuteTime())
                .setWorkerPoolName("app-worker-pool");

        vertx.deployVerticle(new WorkerVerticle(), workerOptions, res -> {
            if (res.succeeded()) {
                log.info(
                        "WorkerVerticle deployed successfully with pool size: {}",
                        config.worker().poolSize());
            } else {
                log.info("Failed to deploy WorkerVerticle: {}", res.cause().getMessage());
            }
        });
    }
}
