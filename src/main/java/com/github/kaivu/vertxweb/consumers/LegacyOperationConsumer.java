package com.github.kaivu.vertxweb.consumers;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegacyOperationConsumer implements EventBusConsumer {

    private static final Logger log = LoggerFactory.getLogger(LegacyOperationConsumer.class);

    @Override
    public String getEventAddress() {
        return "app.worker.operation";
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
