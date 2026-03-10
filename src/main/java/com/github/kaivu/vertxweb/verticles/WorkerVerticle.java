package com.github.kaivu.vertxweb.verticles;

import com.github.kaivu.vertxweb.StartupApp;
import com.github.kaivu.vertxweb.consumers.AnalyticsConsumer;
import com.github.kaivu.vertxweb.consumers.BatchOperationConsumer;
import com.github.kaivu.vertxweb.consumers.EventBusConsumer;
import com.github.kaivu.vertxweb.consumers.HealthCheckConsumer;
import com.github.kaivu.vertxweb.consumers.LegacyOperationConsumer;
import com.google.inject.Injector;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(WorkerVerticle.class);

    private final List<MessageConsumer<JsonObject>> registeredConsumers = new ArrayList<>();

    @Override
    public void start(Promise<Void> startPromise) {
        // Use the shared injector created once in StartupApp — same CircuitBreakerRegistry
        // and singleton instances as AppVerticle, avoiding independent state.
        Injector injector = StartupApp.getInjector();

        EventBus eventBus = vertx.eventBus();

        List<EventBusConsumer> consumers = createConsumers(injector);

        consumers.forEach(consumer -> {
            MessageConsumer<JsonObject> handle = consumer.registerConsumer(eventBus);
            registeredConsumers.add(handle);
            log.info("Registered consumer for address: {}", consumer.getEventAddress());
        });

        startPromise.complete();
        log.info("WorkerVerticle started successfully with {} consumers", consumers.size());
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (registeredConsumers.isEmpty()) {
            stopPromise.complete();
            return;
        }

        log.info("Unregistering {} EventBus consumers...", registeredConsumers.size());

        List<Future<?>> unregisterFutures = new ArrayList<>();
        for (MessageConsumer<JsonObject> consumer : registeredConsumers) {
            unregisterFutures.add(consumer.unregister()
                    .onSuccess(v -> log.debug("Consumer unregistered: {}", consumer.address()))
                    .onFailure(e -> log.warn("Failed to unregister consumer: {}", consumer.address(), e)));
        }

        Future.all(unregisterFutures).onComplete(ar -> {
            log.info("All consumers unregistered. WorkerVerticle stopped.");
            stopPromise.complete();
        });
    }

    private List<EventBusConsumer> createConsumers(Injector injector) {
        List<EventBusConsumer> consumers = new ArrayList<>();
        consumers.add(injector.getInstance(LegacyOperationConsumer.class));
        consumers.add(injector.getInstance(AnalyticsConsumer.class));
        consumers.add(injector.getInstance(BatchOperationConsumer.class));
        consumers.add(injector.getInstance(HealthCheckConsumer.class));
        log.info("Created {} consumer instances from shared injector", consumers.size());
        return consumers;
    }
}
