package com.github.kaivu.vertxweb;

import com.github.kaivu.vertxweb.config.AppModule;
import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.config.ConfigProvider;
import com.github.kaivu.vertxweb.verticles.AppVerticle;
import com.github.kaivu.vertxweb.verticles.WorkerVerticle;
import com.google.inject.Guice;
import com.google.inject.Injector;
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
 * Time: 1:23 AM
 */
public class StartupApp {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(StartupApp.class);
    private static Vertx vertx;
    private static ApplicationConfig cachedConfig;
    private static Injector sharedInjector;
    private static final List<String> deploymentIds = new ArrayList<>();

    /**
     * Returns the single Guice injector shared by AppVerticle and WorkerVerticle.
     * Both verticles obtain their dependencies from this injector, ensuring that stateful
     * singletons such as CircuitBreakerRegistry are shared and not duplicated per verticle.
     */
    public static Injector getInjector() {
        return sharedInjector;
    }

    public static void main(String[] args) {
        try {
            // Load application configuration once and cache it for the process lifetime
            cachedConfig = ConfigProvider.createConfig();
            log.info("Application configuration loaded successfully");

            // Setup graceful shutdown
            setupShutdownHook();

            // Create Vertx instance with tuned thread pool options
            VertxOptions vertxOptions = createVertxOptions(cachedConfig);
            vertx = Vertx.vertx(vertxOptions);
            log.info("Vertx instance created with optimized configuration");

            // Deploy verticles with proper error handling
            deployVerticles(cachedConfig)
                    .onSuccess(v -> {
                        log.info(
                                "All verticles deployed successfully. Application started on port: {}",
                                cachedConfig.server().port());
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
                .setMaxWorkerExecuteTimeUnit(TimeUnit.MILLISECONDS)
                .setMaxEventLoopExecuteTime(deployment.maxEventLoopExecuteTimeMs())
                .setMaxEventLoopExecuteTimeUnit(TimeUnit.MILLISECONDS)
                .setBlockedThreadCheckInterval(deployment.blockedThreadCheckIntervalMs())
                .setBlockedThreadCheckIntervalUnit(TimeUnit.MILLISECONDS)
                .setWarningExceptionTime(deployment.warningExceptionTimeMs())
                .setWarningExceptionTimeUnit(TimeUnit.MILLISECONDS);
    }

    private static Future<Void> deployVerticles(ApplicationConfig config) {
        // Create the shared Guice injector once, before deploying any verticle.
        // Both AppVerticle and WorkerVerticle retrieve their dependencies from this single
        // injector via StartupApp.getInjector(), so CircuitBreakerRegistry state is shared.
        sharedInjector = Guice.createInjector(new AppModule(vertx, config));
        log.info("Shared Guice injector created");

        List<Future<?>> deploymentFutures = new ArrayList<>();

        // Deploy exactly one AppVerticle instance.
        // Horizontal scaling is handled externally by Kubernetes (one pod = one instance).
        DeploymentOptions appOptions = new DeploymentOptions().setInstances(1);

        Future<String> appVerticleFuture = vertx.deployVerticle(AppVerticle.class.getName(), appOptions)
                .onSuccess(id -> {
                    deploymentIds.add(id);
                    log.info("AppVerticle deployed (single instance), deployment ID: {}", id);
                })
                .onFailure(error -> log.error("Failed to deploy AppVerticle", error));
        deploymentFutures.add(appVerticleFuture);

        // Deploy WorkerVerticle with configured options
        var deployment = config.deployment();
        DeploymentOptions workerOptions = new DeploymentOptions()
                .setWorkerPoolSize(config.worker().poolSize())
                .setMaxWorkerExecuteTime(config.worker().maxExecuteTime())
                .setMaxWorkerExecuteTimeUnit(TimeUnit.MILLISECONDS)
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
            // Use cached config for shutdown timeout — no re-parsing
            int shutdownTimeout =
                    cachedConfig != null ? cachedConfig.deployment().shutdownTimeoutSeconds() : 30;
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
