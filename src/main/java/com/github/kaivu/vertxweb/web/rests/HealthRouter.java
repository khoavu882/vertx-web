package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.AppConstants;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class HealthRouter {

    private static final Logger log = LoggerFactory.getLogger(HealthRouter.class);
    private static final String HEALTH_CHECK_EVENT = "app.health.check";

    private final Vertx vertx;
    private final ApplicationConfig appConfig;
    private final long startTime;

    @Inject
    public HealthRouter(Vertx vertx, ApplicationConfig appConfig) {
        this.vertx = vertx;
        this.appConfig = appConfig;
        this.startTime = System.currentTimeMillis();
    }

    public void configureRoutes(Router router) {
        router.get("/health").handler(this::healthCheck);
        router.get("/health/readiness").handler(this::readinessCheck);
        router.get("/health/liveness").handler(this::livenessCheck);
        router.get("/health/detailed").handler(this::detailedHealthCheck);
    }

    private void healthCheck(RoutingContext context) {
        JsonObject healthStatus = new JsonObject()
                .put("status", "UP")
                .put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .put("uptime", getUptimeMs())
                .put("version", "1.0.0");

        context.response()
                .putHeader("content-type", AppConstants.Http.CONTENT_TYPE_JSON)
                .setStatusCode(AppConstants.Status.OK)
                .end(healthStatus.encode());
    }

    private void readinessCheck(RoutingContext context) {
        // Check if application is ready to serve traffic
        checkDependencies()
                .onSuccess(result -> {
                    JsonObject readiness = new JsonObject()
                            .put("status", "READY")
                            .put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                            .put("dependencies", result);

                    context.response()
                            .putHeader("content-type", AppConstants.Http.CONTENT_TYPE_JSON)
                            .setStatusCode(AppConstants.Status.OK)
                            .end(readiness.encode());
                })
                .onFailure(error -> {
                    log.error("Readiness check failed", error);
                    JsonObject failure = new JsonObject()
                            .put("status", "NOT_READY")
                            .put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                            .put("error", error.getMessage());

                    context.response()
                            .putHeader("content-type", AppConstants.Http.CONTENT_TYPE_JSON)
                            .setStatusCode(AppConstants.Status.SERVICE_UNAVAILABLE)
                            .end(failure.encode());
                });
    }

    private void livenessCheck(RoutingContext context) {
        // Basic liveness check - if we can respond, we're alive
        long uptime = getUptimeMs();
        boolean isAlive = uptime > 0 && !Thread.currentThread().isInterrupted();

        JsonObject liveness = new JsonObject()
                .put("status", isAlive ? "ALIVE" : "DEAD")
                .put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .put("uptime", uptime);

        context.response()
                .putHeader("content-type", AppConstants.Http.CONTENT_TYPE_JSON)
                .setStatusCode(isAlive ? AppConstants.Status.OK : AppConstants.Status.SERVICE_UNAVAILABLE)
                .end(liveness.encode());
    }

    private void detailedHealthCheck(RoutingContext context) {
        checkDependencies()
                .onSuccess(dependencies -> {
                    MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
                    MemoryUsage heapMemory = memoryBean.getHeapMemoryUsage();
                    MemoryUsage nonHeapMemory = memoryBean.getNonHeapMemoryUsage();

                    JsonObject memory = new JsonObject()
                            .put(
                                    "heap",
                                    new JsonObject()
                                            .put("used", heapMemory.getUsed())
                                            .put("committed", heapMemory.getCommitted())
                                            .put("max", heapMemory.getMax())
                                            .put(
                                                    "usedPercentage",
                                                    (double) heapMemory.getUsed() / heapMemory.getMax() * 100))
                            .put(
                                    "nonHeap",
                                    new JsonObject()
                                            .put("used", nonHeapMemory.getUsed())
                                            .put("committed", nonHeapMemory.getCommitted())
                                            .put("max", nonHeapMemory.getMax()));

                    JsonObject system = new JsonObject()
                            .put("processors", Runtime.getRuntime().availableProcessors())
                            .put("javaVersion", System.getProperty("java.version"))
                            .put("osName", System.getProperty("os.name"))
                            .put("osArch", System.getProperty("os.arch"));

                    JsonObject detailed = new JsonObject()
                            .put("status", "UP")
                            .put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                            .put("uptime", getUptimeMs())
                            .put("dependencies", dependencies)
                            .put("memory", memory)
                            .put("system", system)
                            .put(
                                    "configuration",
                                    new JsonObject()
                                            .put(
                                                    "serverPort",
                                                    appConfig.server().port())
                                            .put(
                                                    "workerPoolSize",
                                                    appConfig.worker().poolSize()));

                    context.response()
                            .putHeader("content-type", AppConstants.Http.CONTENT_TYPE_JSON)
                            .setStatusCode(AppConstants.Status.OK)
                            .end(detailed.encode());
                })
                .onFailure(error -> {
                    log.error("Detailed health check failed", error);
                    JsonObject failure = new JsonObject()
                            .put("status", "DEGRADED")
                            .put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                            .put("error", error.getMessage());

                    context.response()
                            .putHeader("content-type", AppConstants.Http.CONTENT_TYPE_JSON)
                            .setStatusCode(AppConstants.Status.SERVICE_UNAVAILABLE)
                            .end(failure.encode());
                });
    }

    private Future<JsonObject> checkDependencies() {
        // Check EventBus connectivity
        DeliveryOptions options = new DeliveryOptions().setSendTimeout(5000);
        JsonObject healthCheckMessage =
                new JsonObject().put("type", "health-check").put("timestamp", System.currentTimeMillis());

        return vertx.eventBus()
                .<JsonObject>request(HEALTH_CHECK_EVENT, healthCheckMessage, options)
                .map(reply -> new JsonObject()
                        .put(
                                "eventBus",
                                new JsonObject()
                                        .put("status", "UP")
                                        .put(
                                                "responseTime",
                                                System.currentTimeMillis() - healthCheckMessage.getLong("timestamp"))))
                .recover(error -> {
                    log.warn("EventBus health check failed: {}", error.getMessage());
                    return Future.succeededFuture(new JsonObject()
                            .put(
                                    "eventBus",
                                    new JsonObject().put("status", "DOWN").put("error", error.getMessage())));
                });
    }

    private long getUptimeMs() {
        return System.currentTimeMillis() - startTime;
    }
}
