package com.github.kaivu.vertxweb.consumers;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.google.inject.Inject;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegacyOperationConsumer implements EventBusConsumer {

    private static final Logger log = LoggerFactory.getLogger(LegacyOperationConsumer.class);
    private final ApplicationConfig appConfig;

    @Inject
    public LegacyOperationConsumer(ApplicationConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public String getEventAddress() {
        return appConfig.eventBus().legacyOperationAddress();
    }

    @Override
    public void registerConsumer(EventBus eventBus) {
        eventBus.consumer(getEventAddress(), this::handle);
    }

    public void handle(Message<String> message) {
        log.info("Processing legacy operation");
        message.reply("Operation completed");
    }
}
