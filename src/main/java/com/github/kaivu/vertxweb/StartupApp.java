package com.github.kaivu.vertxweb;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.config.ConfigProvider;
import com.github.kaivu.vertxweb.verticles.AppVerticle;
import com.github.kaivu.vertxweb.verticles.WorkerVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

/**
 * Created by Khoa Vu.
 * Mail: kai.vu.dev@gmail.com
 * Date: 8/1/25
 * Time: 1:23 AM
 */
public class StartupApp {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(StartupApp.class);
    private static Vertx vertx;
    private static final List<String> deploymentIds = new ArrayList<>();

    public static void main(String[] args) {
        try {
            // Load application configuration
            ApplicationConfig config = ConfigProvider.createConfig();
            log.info("Application configuration loaded successfully");

            // Setup graceful shutdown
            setupShutdownHook();

            // Create Vertx instance with optimized options
            VertxOptions vertxOptions = createVertxOptions(config);
            vertx = Vertx.vertx(vertxOptions);
            log.info("Vertx instance created with optimized configuration");

            // Deploy verticles with proper error handling
            deployVerticles(config)
                    .onSuccess(v -> {
                        log.info(
                                "All verticles deployed successfully. Application started on port: {}",
                                config.server().port());
                    })
                    .onFailure(error -> {
                        log.error("Failed to deploy verticles. Shutting down application", error);
                        shutdown();
                        System.exit(1);
                    });

        } catch (Exception e) {
            log.error("Fatal error during application startup", e);
            System.exit(1);
        }
    }

    private static VertxOptions createVertxOptions(ApplicationConfig config) {
        var deployment = config.deployment();

        int eventLoopPoolSize = deployment.enableEventLoopPoolAutoSizing()
                ? Runtime.getRuntime().availableProcessors()
                : config.worker().poolSize();

        return new VertxOptions()
                .setEventLoopPoolSize(eventLoopPoolSize)
                .setWorkerPoolSize(config.worker().poolSize() * deployment.workerPoolSizeMultiplier())
                .setMaxWorkerExecuteTime(config.worker().maxExecuteTime())
                .setMaxEventLoopExecuteTime(deployment.maxEventLoopExecuteTimeMs())
                .setBlockedThreadCheckInterval(deployment.blockedThreadCheckIntervalMs())
                .setWarningExceptionTime(deployment.warningExceptionTimeMs());
    }

    private static Future<Void> deployVerticles(ApplicationConfig config) {
        List<Future<?>> deploymentFutures = new ArrayList<>();

        // Deploy multiple AppVerticle instances for load balancing
        var deployment = config.deployment();
        int appVerticleInstances = deployment.enableAppVerticleAutoSizing()
                ? Math.max(
                        deployment.minAppVerticleInstances(),
                        Runtime.getRuntime().availableProcessors() / deployment.appVerticleInstanceDivisor())
                : deployment.minAppVerticleInstances();
        DeploymentOptions appOptions = new DeploymentOptions().setInstances(appVerticleInstances);

        Future<String> appVerticleFuture = vertx.deployVerticle(AppVerticle.class.getName(), appOptions)
                .onSuccess(id -> {
                    deploymentIds.add(id);
                    log.info("AppVerticle deployed with {} instances, deployment ID: {}", appVerticleInstances, id);
                })
                .onFailure(error -> log.error("Failed to deploy AppVerticle", error));
        deploymentFutures.add(appVerticleFuture);

        // Deploy WorkerVerticle with configured options
        DeploymentOptions workerOptions = new DeploymentOptions()
                .setWorkerPoolSize(config.worker().poolSize())
                .setMaxWorkerExecuteTime(config.worker().maxExecuteTime())
                .setWorkerPoolName(deployment.workerPoolName())
                .setThreadingModel(ThreadingModel.WORKER);

        Future<String> workerVerticleFuture = vertx.deployVerticle(WorkerVerticle.class.getName(), workerOptions)
                .onSuccess(id -> {
                    deploymentIds.add(id);
                    log.info(
                            "WorkerVerticle deployed with pool size: {}, deployment ID: {}",
                            config.worker().poolSize(),
                            id);
                })
                .onFailure(error -> log.error("Failed to deploy WorkerVerticle", error));
        deploymentFutures.add(workerVerticleFuture);

        return Future.all(deploymentFutures).mapEmpty();
    }

    private static void setupShutdownHook() {
        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> {
                            log.info("Shutdown hook triggered. Starting graceful shutdown...");
                            shutdown();
                        },
                        "shutdown-hook"));
    }

    private static void shutdown() {
        if (vertx == null) {
            return;
        }

        log.info("Initiating graceful shutdown...");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> errorRef = new AtomicReference<>();

        // Undeploy all verticles first
        List<Future<?>> undeployFutures = new ArrayList<>();
        for (String deploymentId : deploymentIds) {
            undeployFutures.add(vertx.undeploy(deploymentId)
                    .onSuccess(v -> log.info("Undeployed verticle: {}", deploymentId))
                    .onFailure(error -> log.error("Failed to undeploy verticle: {}", deploymentId, error)));
        }

        Future.all(undeployFutures).onComplete(ar -> {
            // Close Vertx instance
            vertx.close(closeResult -> {
                if (closeResult.succeeded()) {
                    log.info("Vertx instance closed successfully");
                } else {
                    log.error("Error closing Vertx instance", closeResult.cause());
                    errorRef.set(closeResult.cause().getMessage());
                }
                latch.countDown();
            });
        });

        try {
            // Wait for graceful shutdown with configured timeout
            ApplicationConfig config = ConfigProvider.createConfig();
            int shutdownTimeout = config.deployment().shutdownTimeoutSeconds();
            if (!latch.await(shutdownTimeout, TimeUnit.SECONDS)) {
                log.warn("Graceful shutdown timed out after {} seconds", shutdownTimeout);
            } else if (errorRef.get() == null) {
                log.info("Application shutdown completed successfully");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Shutdown process interrupted", e);
        }
    }
}
