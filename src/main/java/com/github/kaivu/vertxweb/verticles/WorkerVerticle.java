package com.github.kaivu.vertxweb.verticles;

import com.github.kaivu.vertxweb.consumers.AnalyticsConsumer;
import com.github.kaivu.vertxweb.consumers.BatchOperationConsumer;
import com.github.kaivu.vertxweb.consumers.EventBusConsumer;
import com.github.kaivu.vertxweb.consumers.LegacyOperationConsumer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(WorkerVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) {
        EventBus eventBus = vertx.eventBus();

        // Initialize all consumer classes
        List<EventBusConsumer> consumers = createConsumers();

        // Register all consumers
        consumers.forEach(consumer -> {
            consumer.registerConsumer(eventBus);
            log.info("Registered consumer for address: {}", consumer.getEventAddress());
        });

        startPromise.complete();
        log.info("WorkerVerticle started successfully with {} consumers", consumers.size());
    }

    private List<EventBusConsumer> createConsumers() {
        List<EventBusConsumer> consumers = new ArrayList<>();

        // Add all business consumers
        consumers.add(new LegacyOperationConsumer());
        consumers.add(new AnalyticsConsumer());
        consumers.add(new BatchOperationConsumer());

        return consumers;
    }
}
