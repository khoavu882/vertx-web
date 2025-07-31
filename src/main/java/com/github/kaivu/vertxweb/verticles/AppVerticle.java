package com.github.kaivu.vertxweb.verticles;

import com.github.kaivu.vertxweb.config.AppModule;
import com.github.kaivu.vertxweb.web.routes.RouterConfig;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.core.AbstractVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(AppVerticle.class);

    @Override
    public Uni<Void> asyncStart() {
        Injector injector = Guice.createInjector(new AppModule(vertx));
        RouterConfig routerConfig = injector.getInstance(RouterConfig.class);

        int port = config().getInteger("http.port", 8080);
        return vertx.createHttpServer()
                .requestHandler(routerConfig.getRouter())
                .listen(port)
                .onItem()
                .invoke(server -> log.info("HTTP server started on port {}", port))
                .replaceWithVoid();
    }

    @Override
    public Uni<Void> asyncStop() {
        log.info("Stopping AppVerticle");
        return Uni.createFrom().voidItem().onItem().invoke(() -> log.info("✅ AppVerticle stopped"));
    }
}
