package com.github.kaivu.vertxweb.config;

import com.github.kaivu.vertxweb.middlewares.AuthHandler;
import com.github.kaivu.vertxweb.middlewares.ErrorHandler;
import com.github.kaivu.vertxweb.middlewares.LoggingHandler;
import com.github.kaivu.vertxweb.services.ProductService;
import com.github.kaivu.vertxweb.services.UserService;
import com.github.kaivu.vertxweb.web.RouterHelper;
import com.github.kaivu.vertxweb.web.rests.CommonRouter;
import com.github.kaivu.vertxweb.web.rests.ProductRouter;
import com.github.kaivu.vertxweb.web.rests.UserRouter;
import com.github.kaivu.vertxweb.web.routes.RouterConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

/**
 * Google Guice module for dependency injection configuration.
 *
 * This module follows Guice best practices:
 * - Uses @Provides for complex object creation
 * - Leverages constructor injection where possible via @Inject annotations
 * - Properly scopes singletons for stateful components
 * - Separates configuration binding from service binding
 */
public class AppModule extends AbstractModule {
    private final Vertx vertx;
    private final ApplicationConfig applicationConfig;

    public AppModule(Vertx vertx) {
        this.vertx = vertx;
        this.applicationConfig = ConfigProvider.createConfig();
    }

    @Override
    protected void configure() {
        // Bind external instances that cannot be created by Guice
        bind(Vertx.class).toInstance(vertx);
        bind(ApplicationConfig.class).toInstance(applicationConfig);

        // Bind services - these have @Inject constructors, so Guice handles creation
        bind(UserService.class).in(Singleton.class);
        bind(ProductService.class).in(Singleton.class);

        // Bind middleware handlers
        bind(AuthHandler.class).in(Singleton.class);
        bind(LoggingHandler.class).in(Singleton.class);
        bind(ErrorHandler.class).in(Singleton.class);

        // Bind utility helpers
        bind(RouterHelper.class).in(Singleton.class);

        // Bind routers - these also have @Inject constructors
        bind(CommonRouter.class).in(Singleton.class);
        bind(UserRouter.class).in(Singleton.class);
        bind(ProductRouter.class).in(Singleton.class);

        // Bind router configuration
        bind(RouterConfig.class).in(Singleton.class);
    }

    /**
     * Provides the main Vert.x Router instance.
     * This needs a provider method because Router.router() is a factory method.
     */
    @Provides
    @Singleton
    Router provideMainRouter(Vertx vertx) {
        return Router.router(vertx);
    }
}
