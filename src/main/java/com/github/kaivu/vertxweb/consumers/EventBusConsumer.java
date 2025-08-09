package com.github.kaivu.vertxweb.consumers;

import io.vertx.core.eventbus.EventBus;

public interface EventBusConsumer {

    String getEventAddress();

    void registerConsumer(EventBus eventBus);
}
