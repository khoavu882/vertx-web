package com.github.kaivu.vertxweb.verticles;

import com.github.kaivu.vertxweb.config.AppModule;
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
        Injector injector = Guice.createInjector(new AppModule(vertx));
        RouterConfig routerConfig = injector.getInstance(RouterConfig.class);

        int port = config().getInteger("http.port", 8080);
        vertx.createHttpServer().requestHandler(routerConfig.getRouter()).listen(port, http -> {
            if (http.succeeded()) {
                startPromise.complete();
                log.info("HTTP server started on port {}", port);
            } else {
                startPromise.fail(http.cause());
            }
        });
    }
}
