package com.github.kaivu.vertxweb.verticles;

import com.github.kaivu.vertxweb.config.AppModule;
import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.web.routes.RouterConfig;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(AppVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) {
        // Create Guice injector with all dependencies
        Injector injector = Guice.createInjector(new AppModule(vertx));

        // Get configuration and router from injector
        ApplicationConfig appConfig = injector.getInstance(ApplicationConfig.class);
        RouterConfig routerConfig = injector.getInstance(RouterConfig.class);

        // Use configured port, with fallback to Vert.x config, then default
        int configuredPort = appConfig.server().port();
        int port = config().getInteger("http.port", configuredPort);
        String host = appConfig.server().host();

        log.info("Starting HTTP server on {}:{}", host, port);

        vertx.createHttpServer().requestHandler(routerConfig.getRouter()).listen(port, host, http -> {
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
}
