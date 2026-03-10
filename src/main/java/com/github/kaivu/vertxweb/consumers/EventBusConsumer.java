package com.github.kaivu.vertxweb.consumers;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

public interface EventBusConsumer {

    String getEventAddress();

    /** Registers this consumer on the EventBus and returns the handle for later unregistration. */
    MessageConsumer<JsonObject> registerConsumer(EventBus eventBus);
}
