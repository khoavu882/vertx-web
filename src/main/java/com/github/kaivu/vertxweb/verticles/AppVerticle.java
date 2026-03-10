package com.github.kaivu.vertxweb.verticles;

import com.github.kaivu.vertxweb.StartupApp;
import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.web.routes.RouterConfig;
import com.google.inject.Injector;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(AppVerticle.class);

    private HttpServer httpServer;

    @Override
    public void start(Promise<Void> startPromise) {
        // Use the shared injector created once in StartupApp.
        // This ensures AppVerticle and WorkerVerticle share the same CircuitBreakerRegistry,
        // MetricsFacade, and other stateful singletons.
        Injector injector = StartupApp.getInjector();
        ApplicationConfig appConfig = injector.getInstance(ApplicationConfig.class);
        RouterConfig routerConfig = injector.getInstance(RouterConfig.class);

        int port = config().getInteger("http.port", appConfig.server().port());
        String host = appConfig.server().host();

        log.info("Starting HTTP server on {}:{}", host, port);

        httpServer = vertx.createHttpServer();
        httpServer.requestHandler(routerConfig.getRouter()).listen(port, host, http -> {
            if (http.succeeded()) {
                startPromise.complete();
                log.info("HTTP server started successfully on {}:{}", host, port);
                log.info(
                        "API available at: http://{}:{}{}",
                        host,
                        port,
                        appConfig.server().apiPrefix());
            } else {
                log.error("Failed to start HTTP server", http.cause());
                startPromise.fail(http.cause());
            }
        });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (httpServer != null) {
            log.info("Closing HTTP server...");
            httpServer.close(result -> {
                if (result.succeeded()) {
                    log.info("HTTP server closed successfully");
                } else {
                    log.warn("HTTP server close completed with error", result.cause());
                }
                stopPromise.complete();
            });
        } else {
            stopPromise.complete();
        }
    }
}
