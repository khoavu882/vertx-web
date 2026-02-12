package com.github.kaivu.vertxweb.config;

import com.github.kaivu.vertxweb.constants.*;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Application configuration interface using SmallRye Config.
 *
 * This interface replaces the old AppConfig class and uses SmallRye Config
 * annotations for automatic property binding from application.yml
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
     * EventBus address configuration section.
     */
    EventBusConfig eventBus();

    /**
     * Validation configuration section.
     */
    ValidationConfig validation();

    /**
     * Deployment configuration section for Vertx options and deployment settings.
     */
    DeploymentConfig deployment();

    /**
     * Observability configuration section for health and metrics.
     */
    ObservabilityConfig observability();

    interface ServerConfig {
        @WithDefault("8080")
        int port();

        @WithDefault("0.0.0.0")
        String host();

        @WithDefault("30000")
        long requestTimeoutMs();

        @WithDefault("/api")
        String apiPrefix();

        CorsConfig cors();

        @WithDefault("1048576")
        long maxBodySizeBytes();

        interface CorsConfig {
            @WithDefault("true")
            boolean enable();

            @WithDefault("*")
            String allowedOrigins();

            @WithDefault("false")
            boolean allowCredentials();
        }
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

        @WithDefault("false")
        boolean includeStackTraceInErrorResponse();
    }

    interface ServiceConfig {
        @WithDefault("500")
        int defaultTimeoutMs();

        @WithDefault("5")
        int circuitBreakerFailureThreshold();

        @WithDefault("3")
        int circuitBreakerSuccessThreshold();

        @WithDefault("10000")
        int circuitBreakerTimeoutMs();

        @WithDefault("60000")
        int circuitBreakerResetTimeoutMs();

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
        @WithDefault(EventBusConstants.ANALYTICS_REPORT)
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

    interface EventBusConfig {
        @WithDefault(EventBusConstants.HEALTH_CHECK)
        String healthCheckAddress();

        @WithDefault(EventBusConstants.BATCH_OPERATION)
        String batchOperationAddress();

        @WithDefault(EventBusConstants.LEGACY_OPERATION)
        String legacyOperationAddress();
    }

    interface ValidationConfig {
        @WithDefault("100")
        int maxNameLength();

        @WithDefault("1000")
        int batchProcessedRecords();
    }

    interface DeploymentConfig {
        @WithDefault("true")
        boolean enableEventLoopPoolAutoSizing();

        @WithDefault("2")
        int workerPoolSizeMultiplier();

        @WithDefault("5000")
        long maxEventLoopExecuteTimeMs();

        @WithDefault("1000")
        long blockedThreadCheckIntervalMs();

        @WithDefault("5000")
        long warningExceptionTimeMs();

        @WithDefault("true")
        boolean enableAppVerticleAutoSizing();

        @WithDefault("2")
        int appVerticleInstanceDivisor();

        @WithDefault("1")
        int minAppVerticleInstances();

        @WithDefault("app-worker-pool")
        String workerPoolName();

        @WithDefault("30")
        int shutdownTimeoutSeconds();
    }

    interface ObservabilityConfig {
        HealthConfig health();

        MetricsConfig metrics();

        TracingConfig tracing();

        interface HealthConfig {
            @WithDefault("true")
            boolean enable();

            @WithDefault("true")
            boolean exposeDetailed();

            @WithDefault("1000")
            long checkTimeoutMs();

            @WithDefault("true")
            boolean legacyAliases();
        }

        interface MetricsConfig {
            @WithDefault("true")
            boolean enable();

            @WithDefault("micrometer")
            String backend();

            @WithDefault(PathConstants.METRICS_ROOT)
            String endpointPath();

            @WithDefault("protected")
            String exposure();

            @WithDefault("true")
            boolean includeJvm();

            @WithDefault("true")
            boolean includeSystem();
        }

        interface TracingConfig {
            @WithDefault("true")
            boolean enable();

            @WithDefault("vertx-web")
            String serviceName();

            @WithDefault("logging")
            String exporter();

            @WithDefault("http://localhost:4317")
            String otlpEndpoint();

            @WithDefault("1.0")
            double samplingRatio();
        }
    }
}
