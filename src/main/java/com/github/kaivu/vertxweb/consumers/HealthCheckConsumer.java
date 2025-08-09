package com.github.kaivu.vertxweb.consumers;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.google.inject.Inject;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthCheckConsumer implements EventBusConsumer {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckConsumer.class);
    private final ApplicationConfig appConfig;

    @Inject
    public HealthCheckConsumer(ApplicationConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public String getEventAddress() {
        return "app.health.check";
    }

    @Override
    public void registerConsumer(EventBus eventBus) {
        eventBus.<JsonObject>consumer(getEventAddress(), this::handle);
    }

    public void handle(Message<JsonObject> message) {
        try {
            JsonObject request = message.body();
            log.debug("Health check request received: {}", request);

            // Perform basic health checks
            JsonObject response = new JsonObject()
                    .put("status", "UP")
                    .put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .put("workerVerticleHealth", "UP")
                    .put("configurationLoaded", appConfig != null)
                    .put("requestTimestamp", request.getLong("timestamp", System.currentTimeMillis()));

            message.reply(response);
            log.debug("Health check response sent: {}", response);

        } catch (Exception e) {
            log.error("Health check failed", e);
            JsonObject errorResponse = new JsonObject()
                    .put("status", "DOWN")
                    .put("error", e.getMessage())
                    .put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            message.reply(errorResponse);
        }
    }
}
