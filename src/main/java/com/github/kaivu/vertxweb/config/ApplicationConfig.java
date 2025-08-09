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

    /**
     * Service configuration section for timeout and delay values.
     */
    ServiceConfig service();

    /**
     * Analytics configuration section.
     */
    AnalyticsConfig analytics();

    /**
     * Validation configuration section.
     */
    ValidationConfig validation();

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

    interface ServiceConfig {
        @WithDefault("500")
        int defaultTimeoutMs();

        @WithDefault("100")
        int baseDelayMs();

        @WithDefault("200")
        int maxDelayVarianceMs();

        @WithDefault("1000")
        int minIdRange();

        @WithDefault("9999")
        int maxIdRange();

        @WithDefault("50")
        int userFetchBaseDelayMs();

        @WithDefault("100")
        int userFetchMaxVarianceMs();

        @WithDefault("200")
        int createBaseDelayMs();

        @WithDefault("300")
        int createMaxVarianceMs();

        @WithDefault("300")
        int productCreateBaseDelayMs();

        @WithDefault("400")
        int productCreateMaxVarianceMs();

        @WithDefault("150")
        int updateBaseDelayMs();

        @WithDefault("200")
        int updateMaxVarianceMs();

        @WithDefault("100")
        int deleteBaseDelayMs();

        @WithDefault("150")
        int deleteMaxVarianceMs();
    }

    interface AnalyticsConfig {
        @WithDefault("app.worker.analytics-report")
        String eventAddress();

        @WithDefault("2000")
        int databaseQueryDelayMs();

        @WithDefault("1000")
        int fileProcessingDelayMs();

        @WithDefault("500")
        int executionTimeoutMs();

        @WithDefault("1000")
        int maxProducts();

        @WithDefault("100")
        int minProducts();

        @WithDefault("100000")
        double maxRevenue();

        @WithDefault("500")
        double maxOrderValue();

        @WithDefault("50")
        double minOrderValue();

        @WithDefault("3600000")
        long requestExpirationMs();
    }

    interface ValidationConfig {
        @WithDefault("100")
        int maxNameLength();

        @WithDefault("1000")
        int batchProcessedRecords();
    }
}
