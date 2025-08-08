package com.github.kaivu.vertxweb.config;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
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
     * - Load from application.properties (if present)
     * - Override with system properties
     * - Override with environment variables
     * - Map the configuration to the ApplicationConfig interface
     *
     * @return configured ApplicationConfig instance
     */
    public static ApplicationConfig createConfig() {
        try {
            SmallRyeConfig config = new SmallRyeConfigBuilder()
                    .addDefaultSources() // Includes classpath:application.properties, system props, and env vars
                    .withMapping(ApplicationConfig.class)
                    .build();

            ApplicationConfig appConfig = config.getConfigMapping(ApplicationConfig.class);
            logConfiguration(appConfig);
            return appConfig;
        } catch (Exception e) {
            log.error("Failed to load configuration, using defaults", e);
            // Return a default configuration if loading fails
            return createDefaultConfig();
        }
    }

    private static ApplicationConfig createDefaultConfig() {
        log.warn("Using default configuration values");
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .addDefaultSources()
                .withMapping(ApplicationConfig.class)
                .build();
        return config.getConfigMapping(ApplicationConfig.class);
    }

    private static void logConfiguration(ApplicationConfig config) {
        log.info("Application configuration loaded:");
        log.info(
                "  Server - Host: {}, Port: {}, API Prefix: {}",
                config.server().host(),
                config.server().port(),
                config.server().apiPrefix());
        log.info("  Worker Pool Size: {}", config.worker().poolSize());
        log.info(
                "  Security - Auth Enabled: {}, Public Paths: {}",
                config.security().enableAuth(),
                config.security().publicPaths());
        log.info(
                "  Logging - Request Logging: {}, Log Level: {}",
                config.logging().enableRequestLogging(),
                config.logging().logLevel());
    }
}
