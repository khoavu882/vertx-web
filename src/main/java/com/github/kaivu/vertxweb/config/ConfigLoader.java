package com.github.kaivu.vertxweb.config;

import io.vertx.core.json.JsonObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigLoader {
    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    private ConfigLoader() {
        // Utility class
    }

    public static AppConfig load() {
        JsonObject baseConfig = loadFromFile();
        JsonObject configWithEnvOverrides = applyEnvironmentOverrides(baseConfig);
        AppConfig config = AppConfig.from(configWithEnvOverrides);
        logConfiguration(config);
        return config;
    }

    private static JsonObject loadFromFile() {
        JsonObject config = loadFromJson();
        if (config == null) {
            log.info("No configuration file found, using defaults");
            config = AppConfig.defaults().raw();
        }
        return config;
    }

    private static JsonObject loadFromJson() {
        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream("application.json")) {
            if (is != null) {
                log.info("Loading configuration from application.json");
                String content = new Scanner(is, StandardCharsets.UTF_8)
                        .useDelimiter("\\A")
                        .next();
                return new JsonObject(content);
            }
        } catch (Exception e) {
            log.warn("Failed to load configuration from application.json: {}", e.getMessage());
        }
        return null;
    }

    private static JsonObject applyEnvironmentOverrides(JsonObject config) {
        JsonObject result = config.copy();
        JsonObject server = result.getJsonObject("server", new JsonObject());
        JsonObject worker = result.getJsonObject("worker", new JsonObject());
        JsonObject security = result.getJsonObject("security", new JsonObject());

        // Server overrides
        String port = System.getenv("SERVER_PORT");
        if (port != null) {
            try {
                server.put("port", Integer.parseInt(port));
                log.info("Override server port from environment: {}", port);
            } catch (NumberFormatException e) {
                log.warn("Invalid SERVER_PORT environment variable: {}", port);
            }
        }

        String host = System.getenv("SERVER_HOST");
        if (host != null) {
            server.put("host", host);
            log.info("Override server host from environment: {}", host);
        }

        String apiPrefix = System.getenv("API_PREFIX");
        if (apiPrefix != null) {
            server.put("apiPrefix", apiPrefix);
            log.info("Override API prefix from environment: {}", apiPrefix);
        }

        // Worker overrides
        String workerPoolSize = System.getenv("WORKER_POOL_SIZE");
        if (workerPoolSize != null) {
            try {
                worker.put("poolSize", Integer.parseInt(workerPoolSize));
                log.info("Override worker pool size from environment: {}", workerPoolSize);
            } catch (NumberFormatException e) {
                log.warn("Invalid WORKER_POOL_SIZE environment variable: {}", workerPoolSize);
            }
        }

        // Security overrides
        String jwtSecret = System.getenv("JWT_SECRET");
        if (jwtSecret != null && !jwtSecret.trim().isEmpty()) {
            security.put("jwtSecret", jwtSecret);
            log.info("Override JWT secret from environment (redacted)");
        }

        String enableAuth = System.getenv("ENABLE_AUTH");
        if (enableAuth != null) {
            security.put("enableAuth", Boolean.parseBoolean(enableAuth));
            log.info("Override auth enabled from environment: {}", enableAuth);
        }

        // Apply all sections back to result
        result.put("server", server);
        result.put("worker", worker);
        result.put("security", security);

        return result;
    }

    private static void logConfiguration(AppConfig config) {
        log.info("Application configuration:");
        log.info(
                "  Server - Host: {}, Port: {}, API Prefix: {}",
                config.getServerHost(),
                config.getServerPort(),
                config.getApiPrefix());
        log.info("  Worker Pool Size: {}", config.getWorkerPoolSize());
        log.info("  Security - Auth Enabled: {}, Public Paths: {}", config.isAuthEnabled(), config.getPublicPaths());
        log.info(
                "  Logging - Request Logging: {}, Log Level: {}",
                config.isRequestLoggingEnabled(),
                config.getLogLevel());
    }
}
