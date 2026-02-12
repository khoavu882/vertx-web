package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.observability.health.CheckResult;
import com.github.kaivu.vertxweb.observability.health.CheckStatus;
import com.github.kaivu.vertxweb.observability.health.HealthPayload;
import com.github.kaivu.vertxweb.observability.health.ProbeOrchestrator;
import com.github.kaivu.vertxweb.observability.metrics.MetricsFacade;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
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
    private final ProbeOrchestrator probeOrchestrator;
    private final MetricsFacade metricsFacade;
    private final long startTime;

    @Inject
    public HealthRouter(
            Vertx vertx,
            ApplicationConfig appConfig,
            ProbeOrchestrator probeOrchestrator,
            MetricsFacade metricsFacade) {
        this.vertx = vertx;
        this.appConfig = appConfig;
        this.probeOrchestrator = probeOrchestrator;
        this.metricsFacade = metricsFacade;
        this.startTime = System.currentTimeMillis();
    }

    public void configureRoutes(Router router) {
        if (!appConfig.observability().health().enable()) {
            log.info("Health endpoints are disabled by configuration.");
            return;
        }

        router.get("/health").handler(this::overallHealthCheck);
        router.get("/health/live").handler(this::liveHealthCheck);
        router.get("/health/ready").handler(this::readyHealthCheck);
        router.get("/health/started").handler(this::startedHealthCheck);

        if (appConfig.observability().health().legacyAliases()) {
            router.get("/health/readiness").handler(this::readyHealthCheck);
            router.get("/health/liveness").handler(this::liveHealthCheck);
        }

        if (appConfig.observability().health().exposeDetailed()) {
            router.get("/health/detailed").handler(this::detailedHealthCheck);
        }
    }

    private void overallHealthCheck(RoutingContext context) {
        respondWithProbe(context, probeOrchestrator.overall());
    }

    private void liveHealthCheck(RoutingContext context) {
        respondWithProbe(context, probeOrchestrator.live());
    }

    private void readyHealthCheck(RoutingContext context) {
        respondWithProbe(context, probeOrchestrator.ready());
    }

    private void startedHealthCheck(RoutingContext context) {
        respondWithProbe(context, probeOrchestrator.started());
    }

    private void detailedHealthCheck(RoutingContext context) {
        checkDependencies()
                .onSuccess(dependencies -> {
                    boolean dependenciesUp = isDependencyUp(dependencies);
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
                            .put("status", dependenciesUp ? "UP" : "DEGRADED")
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
                            .setStatusCode(
                                    dependenciesUp ? AppConstants.Status.OK : AppConstants.Status.SERVICE_UNAVAILABLE)
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
        long startedAt = System.currentTimeMillis();
        DeliveryOptions options = new DeliveryOptions().setSendTimeout(5000);
        JsonObject healthCheckMessage =
                new JsonObject().put("type", "health-check").put("timestamp", System.currentTimeMillis());

        return vertx.eventBus()
                .<JsonObject>request(HEALTH_CHECK_EVENT, healthCheckMessage, options)
                .map(reply -> {
                    metricsFacade.recordEventBusRequest(
                            HEALTH_CHECK_EVENT, "success", System.currentTimeMillis() - startedAt);
                    return new JsonObject()
                            .put(
                                    "eventBus",
                                    new JsonObject()
                                            .put("status", "UP")
                                            .put(
                                                    "responseTime",
                                                    System.currentTimeMillis()
                                                            - healthCheckMessage.getLong("timestamp")));
                })
                .recover(error -> {
                    metricsFacade.recordEventBusRequest(
                            HEALTH_CHECK_EVENT, "error", System.currentTimeMillis() - startedAt);
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

    private boolean isDependencyUp(JsonObject dependencies) {
        JsonObject eventBusHealth = dependencies != null ? dependencies.getJsonObject("eventBus") : null;
        if (eventBusHealth == null) {
            return false;
        }
        return "UP".equalsIgnoreCase(eventBusHealth.getString("status"));
    }

    private void respondWithProbe(RoutingContext context, Uni<HealthPayload> probe) {
        probe.subscribe()
                .with(
                        payload -> {
                            int statusCode = "UP".equalsIgnoreCase(payload.status())
                                    ? AppConstants.Status.OK
                                    : AppConstants.Status.SERVICE_UNAVAILABLE;
                            context.response()
                                    .putHeader("content-type", AppConstants.Http.CONTENT_TYPE_JSON)
                                    .setStatusCode(statusCode)
                                    .end(toJson(payload).encode());
                        },
                        error -> {
                            log.error("Health probe execution failed", error);
                            JsonObject failure = new JsonObject()
                                    .put("status", "DOWN")
                                    .put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                                    .put("error", error.getMessage());
                            context.response()
                                    .putHeader("content-type", AppConstants.Http.CONTENT_TYPE_JSON)
                                    .setStatusCode(AppConstants.Status.SERVICE_UNAVAILABLE)
                                    .end(failure.encode());
                        });
    }

    private JsonObject toJson(HealthPayload payload) {
        return new JsonObject()
                .put("status", payload.status())
                .put("generatedAt", payload.generatedAtMs())
                .put("checks", payload.checks().stream().map(this::toJson).toList());
    }

    private JsonObject toJson(CheckResult checkResult) {
        JsonObject json = new JsonObject()
                .put("name", checkResult.name())
                .put("type", checkResult.type().name())
                .put("status", checkResult.status() == CheckStatus.UP ? "UP" : "DOWN")
                .put("data", checkResult.data());
        if (checkResult.message() != null && !checkResult.message().isBlank()) {
            json.put("message", checkResult.message());
        }
        return json;
    }
}
