package com.github.kaivu.vertx_web;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;

public class WorkerVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        EventBus eventBus = vertx.eventBus();

        // Register handlers for potentially blocking operations
        eventBus.<String>consumer("app.worker.operation", message -> {
            // Execute blocking operations here
            // For example, database operations, file I/O, etc.

            // Send back the result
            message.reply("Operation completed");
        });

        startPromise.complete();
    }
}
