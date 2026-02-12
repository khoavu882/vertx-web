package com.github.kaivu.vertxweb.constants;

public final class HealthConstants {
    private HealthConstants() {}

    public static final String ROUTE_ROOT = "/health";
    public static final String ROUTE_LIVE = "/health/live";
    public static final String ROUTE_READY = "/health/ready";
    public static final String ROUTE_STARTED = "/health/started";
    public static final String ROUTE_READY_ALIAS = "/health/readiness";
    public static final String ROUTE_LIVE_ALIAS = "/health/liveness";
    public static final String ROUTE_DETAILED = "/health/detailed";
    public static final String STATUS_UP = "UP";
    public static final String STATUS_DOWN = "DOWN";
    public static final String STATUS_DEGRADED = "DEGRADED";
    public static final String KEY_EVENT_BUS = "eventBus";
    public static final String KEY_WORKER_VERTICLE_HEALTH = "workerVerticleHealth";
    public static final String KEY_CONFIGURATION_LOADED = "configurationLoaded";
    public static final String KEY_REQUEST_TIMESTAMP = "requestTimestamp";
    public static final String KEY_RESPONSE_TIME = "responseTime";
    public static final String KEY_RESPONSE_TIME_MS = "responseTimeMs";
    public static final String VALUE_TYPE_HEALTH_CHECK = "health-check";
}
