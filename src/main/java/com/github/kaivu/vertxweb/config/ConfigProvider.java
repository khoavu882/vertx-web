package com.github.kaivu.vertxweb.config;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.Locale;
import java.util.stream.StreamSupport;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration provider for creating SmallRye Config instances.
 *
 * This class provides a factory method for creating properly configured
 * SmallRye Config instances that can be used with Google Guice.
 */
public class ConfigProvider {
    private static final Logger log = LoggerFactory.getLogger(ConfigProvider.class);

    private ConfigProvider() {
        // Utility class
    }

    /**
     * Creates and configures a SmallRye Config instance.
     *
     * The config will automatically:
     * - Load from application.yml (required)
     * - Override with system properties
     * - Override with environment variables
     * - Map the configuration to the ApplicationConfig interface
     *
     * @return configured ApplicationConfig instance
     */
    public static ApplicationConfig createConfig() {
        try {
            SmallRyeConfig config = new SmallRyeConfigBuilder()
                    .addDefaultSources()
                    .addDiscoveredSources()
                    .addDiscoveredConverters()
                    .withMapping(ApplicationConfig.class)
                    .build();

            ensureYamlSourcePresent(config);
            ApplicationConfig appConfig = config.getConfigMapping(ApplicationConfig.class);
            logConfiguration(appConfig);
            return appConfig;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load required application configuration", e);
        }
    }

    private static void ensureYamlSourcePresent(SmallRyeConfig config) {
        boolean yamlSourceFound = StreamSupport.stream(config.getConfigSources().spliterator(), false)
                .map(ConfigSource::getName)
                .filter(name -> name != null && !name.isBlank())
                .anyMatch(ConfigProvider::isYamlLikeSourceName);

        if (!yamlSourceFound) {
            throw new IllegalStateException("Required YAML config source is missing. Ensure application.yml exists and "
                    + "'smallrye-config-source-yaml' is on the classpath.");
        }

        log.info("YAML configuration source detected and locked in.");
    }

    private static boolean isYamlLikeSourceName(String sourceName) {
        String normalized = sourceName.toLowerCase(Locale.ROOT);
        return normalized.contains(".yml") || normalized.contains(".yaml") || normalized.contains("yaml");
    }

    private static void logConfiguration(ApplicationConfig config) {
        log.info("Application configuration loaded:");
        log.info(
                "  Server - Host: {}, Port: {}, API Prefix: {}, Request Timeout: {}ms, Max Body Size: {} bytes",
                config.server().host(),
                config.server().port(),
                config.server().apiPrefix(),
                config.server().requestTimeoutMs(),
                config.server().maxBodySizeBytes());
        log.info(
                "  CORS - Enabled: {}, Allowed Origins: {}, Allow Credentials: {}",
                config.server().cors().enable(),
                config.server().cors().allowedOrigins(),
                config.server().cors().allowCredentials());
        log.info("  Worker Pool Size: {}", config.worker().poolSize());
        String jwtSecret = config.security().jwtSecret();
        boolean jwtSecretConfigured = jwtSecret != null && !jwtSecret.isBlank();
        log.info(
                "  Security - Auth Enabled: {}, Public Paths: {}, JWT Expiration: {}ms, JWT Secret Configured: {}",
                config.security().enableAuth(),
                config.security().publicPaths(),
                config.security().jwtExpirationMs(),
                jwtSecretConfigured);
        log.info(
                "  Logging - Request Logging: {}, Log Request Bodies: {}, Include Stack Trace: {}, Log Level: {}",
                config.logging().enableRequestLogging(),
                config.logging().logRequestBodies(),
                config.logging().includeStackTraceInErrorResponse(),
                config.logging().logLevel());
        log.info(
                "  Service - Default Timeout: {}ms, Base Delay: {}ms, Circuit Breaker [failures={}, successes={}, timeout={}ms, reset={}ms]",
                config.service().defaultTimeoutMs(),
                config.service().baseDelayMs(),
                config.service().circuitBreakerFailureThreshold(),
                config.service().circuitBreakerSuccessThreshold(),
                config.service().circuitBreakerTimeoutMs(),
                config.service().circuitBreakerResetTimeoutMs());
        log.info("  Validation - Max Name Length: {}", config.validation().maxNameLength());
        log.info(
                "  Analytics - Execution Timeout: {}ms, DB Delay: {}ms, File Delay: {}ms",
                config.analytics().executionTimeoutMs(),
                config.analytics().databaseQueryDelayMs(),
                config.analytics().fileProcessingDelayMs());
        log.info(
                "  EventBus - Health: {}, Batch: {}, Legacy: {}, Analytics: {}",
                config.eventBus().healthCheckAddress(),
                config.eventBus().batchOperationAddress(),
                config.eventBus().legacyOperationAddress(),
                config.analytics().eventAddress());
        log.info(
                "  Observability Health - Enabled: {}, Detailed: {}, Check Timeout: {}ms, Legacy Aliases: {}",
                config.observability().health().enable(),
                config.observability().health().exposeDetailed(),
                config.observability().health().checkTimeoutMs(),
                config.observability().health().legacyAliases());
        log.info(
                "  Observability Metrics - Enabled: {}, Backend: {}, Endpoint: {}, Exposure: {}, JVM: {}, System: {}",
                config.observability().metrics().enable(),
                config.observability().metrics().backend(),
                config.observability().metrics().endpointPath(),
                config.observability().metrics().exposure(),
                config.observability().metrics().includeJvm(),
                config.observability().metrics().includeSystem());
        log.info(
                "  Observability Tracing - Enabled: {}, Service: {}, Exporter: {}, OTLP Endpoint: {}, Sampling Ratio: {}",
                config.observability().tracing().enable(),
                config.observability().tracing().serviceName(),
                config.observability().tracing().exporter(),
                config.observability().tracing().otlpEndpoint(),
                config.observability().tracing().samplingRatio());
    }
}
