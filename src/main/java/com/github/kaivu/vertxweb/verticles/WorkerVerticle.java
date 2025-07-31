package com.github.kaivu.vertxweb.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;

public class WorkerVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        EventBus eventBus = vertx.eventBus();

        // Register handlers for potentially blocking operations
        // Example , database operations, file I/O, etc.
        eventBus.<String>consumer("app.worker.operation", message -> {
            message.reply("Operation completed");
        });

        startPromise.complete();
    }
}
