package com.github.kaivu.vertxweb.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Application configuration interface using SmallRye Config.
 *
 * This interface replaces the old AppConfig class and uses SmallRye Config
 * annotations for automatic property binding from application.properties
 * and environment variables.
 *
 * SmallRye Config automatically maps environment variables using the
 * standard convention (e.g., app.server.port -> APP_SERVER_PORT).
 */
@ConfigMapping(prefix = "app")
public interface ApplicationConfig {

    /**
     * Server configuration section.
     */
    ServerConfig server();

    /**
     * Worker configuration section.
     */
    WorkerConfig worker();

    /**
     * Security configuration section.
     */
    SecurityConfig security();

    /**
     * Logging configuration section.
     */
    LoggingConfig logging();

    interface ServerConfig {
        @WithDefault("8080")
        int port();

        @WithDefault("0.0.0.0")
        String host();

        @WithDefault("30000")
        long requestTimeoutMs();

        @WithDefault("/api")
        String apiPrefix();

        @WithDefault("true")
        boolean enableCors();
    }

    interface WorkerConfig {
        @WithDefault("10")
        int poolSize();

        @WithDefault("60000")
        int maxExecuteTime();
    }

    interface SecurityConfig {
        @WithDefault("true")
        boolean enableAuth();

        @WithDefault("your-secret-key")
        String jwtSecret();

        @WithDefault("86400000")
        long jwtExpirationMs();

        @WithDefault("/api/common")
        String publicPaths();
    }

    interface LoggingConfig {
        @WithDefault("true")
        boolean enableRequestLogging();

        @WithDefault("false")
        boolean logRequestBodies();

        @WithDefault("INFO")
        String logLevel();
    }
}
