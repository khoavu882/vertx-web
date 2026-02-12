package com.github.kaivu.vertxweb.consumers;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.*;
import com.github.kaivu.vertxweb.observability.tracing.TracingService;
import com.google.inject.Inject;
import io.opentelemetry.api.trace.Span;
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
    private final TracingService tracingService;

    @Inject
    public HealthCheckConsumer(ApplicationConfig appConfig, TracingService tracingService) {
        this.appConfig = appConfig;
        this.tracingService = tracingService;
    }

    @Override
    public String getEventAddress() {
        return appConfig.eventBus().healthCheckAddress();
    }

    @Override
    public void registerConsumer(EventBus eventBus) {
        eventBus.<JsonObject>consumer(getEventAddress(), this::handle);
    }

    public void handle(Message<JsonObject> message) {
        Span consumerSpan = tracingService.startEventBusConsumerSpan(getEventAddress(), message.body());
        try {
            JsonObject request = message.body();
            log.debug("Health check request received: {}", request);

            // Perform basic health checks
            JsonObject response = new JsonObject()
                    .put(JsonKeys.STATUS, HealthConstants.STATUS_UP)
                    .put(JsonKeys.TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .put(HealthConstants.KEY_WORKER_VERTICLE_HEALTH, HealthConstants.STATUS_UP)
                    .put(HealthConstants.KEY_CONFIGURATION_LOADED, appConfig != null)
                    .put(
                            HealthConstants.KEY_REQUEST_TIMESTAMP,
                            request.getLong(JsonKeys.TIMESTAMP, System.currentTimeMillis()));

            tracingService.endSpan(consumerSpan, null);
            message.reply(response);
            log.debug("Health check response sent: {}", response);

        } catch (Exception e) {
            log.error("Health check failed", e);
            tracingService.endSpan(consumerSpan, e);
            JsonObject errorResponse = new JsonObject()
                    .put(JsonKeys.STATUS, HealthConstants.STATUS_DOWN)
                    .put(JsonKeys.ERROR, e.getMessage())
                    .put(JsonKeys.TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            message.reply(errorResponse);
        }
    }
}
