package com.github.kaivu.vertxweb.config;

import io.vertx.core.json.JsonObject;

public class AppConfig {
    private final JsonObject config;

    private AppConfig(JsonObject config) {
        this.config = config != null ? config : getDefaultConfig();
    }

    public static AppConfig from(JsonObject config) {
        return new AppConfig(config);
    }

    public static AppConfig defaults() {
        return new AppConfig(null);
    }

    private static JsonObject getDefaultConfig() {
        return new JsonObject()
                .put(
                        "server",
                        new JsonObject()
                                .put("port", 8080)
                                .put("host", "0.0.0.0")
                                .put("requestTimeoutMs", 30000)
                                .put("apiPrefix", "/api")
                                .put("enableCors", true))
                .put("worker", new JsonObject().put("poolSize", 10).put("maxExecuteTime", 60000))
                .put(
                        "security",
                        new JsonObject()
                                .put("enableAuth", true)
                                .put("jwtSecret", "your-secret-key")
                                .put("jwtExpirationMs", 86400000L)
                                .put("publicPaths", "/api/common"))
                .put(
                        "logging",
                        new JsonObject()
                                .put("enableRequestLogging", true)
                                .put("logRequestBodies", false)
                                .put("logLevel", "INFO"));
    }

    public JsonObject raw() {
        return config.copy();
    }

    // Server configuration
    public int getServerPort() {
        return config.getJsonObject("server", new JsonObject()).getInteger("port", 8080);
    }

    public String getServerHost() {
        return config.getJsonObject("server", new JsonObject()).getString("host", "0.0.0.0");
    }

    public long getRequestTimeoutMs() {
        return config.getJsonObject("server", new JsonObject()).getLong("requestTimeoutMs", 30000L);
    }

    public String getApiPrefix() {
        return config.getJsonObject("server", new JsonObject()).getString("apiPrefix", "/api");
    }

    public boolean isCorsEnabled() {
        return config.getJsonObject("server", new JsonObject()).getBoolean("enableCors", true);
    }

    // Worker configuration
    public int getWorkerPoolSize() {
        return config.getJsonObject("worker", new JsonObject()).getInteger("poolSize", 10);
    }

    public int getWorkerMaxExecuteTime() {
        return config.getJsonObject("worker", new JsonObject()).getInteger("maxExecuteTime", 60000);
    }

    // Security configuration
    public boolean isAuthEnabled() {
        return config.getJsonObject("security", new JsonObject()).getBoolean("enableAuth", true);
    }

    public String getJwtSecret() {
        return config.getJsonObject("security", new JsonObject()).getString("jwtSecret", "your-secret-key");
    }

    public long getJwtExpirationMs() {
        return config.getJsonObject("security", new JsonObject()).getLong("jwtExpirationMs", 86400000L);
    }

    public String getPublicPaths() {
        return config.getJsonObject("security", new JsonObject()).getString("publicPaths", "/api/common");
    }

    // Logging configuration
    public boolean isRequestLoggingEnabled() {
        return config.getJsonObject("logging", new JsonObject()).getBoolean("enableRequestLogging", true);
    }

    public boolean isRequestBodyLoggingEnabled() {
        return config.getJsonObject("logging", new JsonObject()).getBoolean("logRequestBodies", false);
    }

    public String getLogLevel() {
        return config.getJsonObject("logging", new JsonObject()).getString("logLevel", "INFO");
    }
}
