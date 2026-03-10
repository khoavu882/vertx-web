package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.HealthConstants;
import com.github.kaivu.vertxweb.constants.HttpConstants;
import com.github.kaivu.vertxweb.constants.HttpStatusCodes;
import com.github.kaivu.vertxweb.constants.JsonKeys;
import com.github.kaivu.vertxweb.constants.OutcomeConstants;
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
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class HealthRouter {

    private static final Logger log = LoggerFactory.getLogger(HealthRouter.class);

    private final Vertx vertx;
    private final ApplicationConfig appConfig;
    private final ProbeOrchestrator probeOrchestrator;
    private final MetricsFacade metricsFacade;
    private final long startTime;

    @Getter
    private final Router router;

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
        this.router = Router.router(vertx);
        initializeRoutes();
    }

    private void initializeRoutes() {
        if (!appConfig.observability().health().enable()) {
            log.info("Health endpoints are disabled by configuration.");
            return;
        }

        router.get(HealthConstants.ROUTE_ROOT).handler(this::overallHealthCheck);
        router.get(HealthConstants.ROUTE_LIVE).handler(this::liveHealthCheck);
        router.get(HealthConstants.ROUTE_READY).handler(this::readyHealthCheck);
        router.get(HealthConstants.ROUTE_STARTED).handler(this::startedHealthCheck);

        if (appConfig.observability().health().legacyAliases()) {
            router.get(HealthConstants.ROUTE_READY_ALIAS).handler(this::readyHealthCheck);
            router.get(HealthConstants.ROUTE_LIVE_ALIAS).handler(this::liveHealthCheck);
        }

        if (appConfig.observability().health().exposeDetailed()) {
            router.get(HealthConstants.ROUTE_DETAILED).handler(this::detailedHealthCheck);
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
                            .put(
                                    JsonKeys.STATUS,
                                    dependenciesUp ? HealthConstants.STATUS_UP : HealthConstants.STATUS_DEGRADED)
                            .put(JsonKeys.TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
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
                            .putHeader(HttpHeaders.CONTENT_TYPE, HttpConstants.CONTENT_TYPE_JSON)
                            .setStatusCode(dependenciesUp ? HttpStatusCodes.OK : HttpStatusCodes.SERVICE_UNAVAILABLE)
                            .end(detailed.encode());
                })
                .onFailure(error -> {
                    log.error("Detailed health check failed", error);
                    JsonObject failure = new JsonObject()
                            .put(JsonKeys.STATUS, HealthConstants.STATUS_DEGRADED)
                            .put(JsonKeys.TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                            .put(JsonKeys.ERROR, error.getMessage());

                    context.response()
                            .putHeader(HttpHeaders.CONTENT_TYPE, HttpConstants.CONTENT_TYPE_JSON)
                            .setStatusCode(HttpStatusCodes.SERVICE_UNAVAILABLE)
                            .end(failure.encode());
                });
    }

    private Future<JsonObject> checkDependencies() {
        // Check EventBus connectivity
        String healthCheckAddress = appConfig.eventBus().healthCheckAddress();
        long startedAt = System.currentTimeMillis();
        DeliveryOptions options = new DeliveryOptions()
                .setSendTimeout(appConfig.observability().health().checkTimeoutMs());
        JsonObject healthCheckMessage = new JsonObject()
                .put(JsonKeys.TYPE, HealthConstants.VALUE_TYPE_HEALTH_CHECK)
                .put(JsonKeys.TIMESTAMP, System.currentTimeMillis());

        return vertx.eventBus()
                .<JsonObject>request(healthCheckAddress, healthCheckMessage, options)
                .map(reply -> {
                    metricsFacade.recordEventBusRequest(
                            healthCheckAddress, OutcomeConstants.SUCCESS, System.currentTimeMillis() - startedAt);
                    return new JsonObject()
                            .put(
                                    HealthConstants.KEY_EVENT_BUS,
                                    new JsonObject()
                                            .put(JsonKeys.STATUS, HealthConstants.STATUS_UP)
                                            .put(
                                                    HealthConstants.KEY_RESPONSE_TIME,
                                                    System.currentTimeMillis()
                                                            - healthCheckMessage.getLong(JsonKeys.TIMESTAMP)));
                })
                .recover(error -> {
                    metricsFacade.recordEventBusRequest(
                            healthCheckAddress, OutcomeConstants.ERROR, System.currentTimeMillis() - startedAt);
                    log.warn("EventBus health check failed: {}", error.getMessage());
                    return Future.succeededFuture(new JsonObject()
                            .put(
                                    HealthConstants.KEY_EVENT_BUS,
                                    new JsonObject()
                                            .put(JsonKeys.STATUS, HealthConstants.STATUS_DOWN)
                                            .put(JsonKeys.ERROR, error.getMessage())));
                });
    }

    private long getUptimeMs() {
        return System.currentTimeMillis() - startTime;
    }

    private boolean isDependencyUp(JsonObject dependencies) {
        JsonObject eventBusHealth =
                dependencies != null ? dependencies.getJsonObject(HealthConstants.KEY_EVENT_BUS) : null;
        if (eventBusHealth == null) {
            return false;
        }
        return HealthConstants.STATUS_UP.equalsIgnoreCase(eventBusHealth.getString(JsonKeys.STATUS));
    }

    private void respondWithProbe(RoutingContext context, Uni<HealthPayload> probe) {
        probe.subscribe()
                .with(
                        payload -> {
                            int statusCode = HealthConstants.STATUS_UP.equalsIgnoreCase(payload.status())
                                    ? HttpStatusCodes.OK
                                    : HttpStatusCodes.SERVICE_UNAVAILABLE;
                            context.response()
                                    .putHeader(HttpHeaders.CONTENT_TYPE, HttpConstants.CONTENT_TYPE_JSON)
                                    .setStatusCode(statusCode)
                                    .end(toJson(payload).encode());
                        },
                        error -> {
                            log.error("Health probe execution failed", error);
                            JsonObject failure = new JsonObject()
                                    .put(JsonKeys.STATUS, HealthConstants.STATUS_DOWN)
                                    .put(
                                            JsonKeys.TIMESTAMP,
                                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                                    .put(JsonKeys.ERROR, error.getMessage());
                            context.response()
                                    .putHeader(HttpHeaders.CONTENT_TYPE, HttpConstants.CONTENT_TYPE_JSON)
                                    .setStatusCode(HttpStatusCodes.SERVICE_UNAVAILABLE)
                                    .end(failure.encode());
                        });
    }

    private JsonObject toJson(HealthPayload payload) {
        return new JsonObject()
                .put(JsonKeys.STATUS, payload.status())
                .put(JsonKeys.GENERATED_AT, payload.generatedAtMs())
                .put(
                        JsonKeys.CHECKS,
                        payload.checks().stream().map(this::toJson).toList());
    }

    private JsonObject toJson(CheckResult checkResult) {
        JsonObject json = new JsonObject()
                .put(JsonKeys.NAME, checkResult.name())
                .put("type", checkResult.type().name())
                .put(
                        JsonKeys.STATUS,
                        checkResult.status() == CheckStatus.UP
                                ? HealthConstants.STATUS_UP
                                : HealthConstants.STATUS_DOWN)
                .put(JsonKeys.DATA, checkResult.data());
        if (checkResult.message() != null && !checkResult.message().isBlank()) {
            json.put(JsonKeys.MESSAGE, checkResult.message());
        }
        return json;
    }
}
