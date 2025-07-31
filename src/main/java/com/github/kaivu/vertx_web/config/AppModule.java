package com.github.kaivu.vertx_web.config;

import com.github.kaivu.vertx_web.services.ProductService;
import com.github.kaivu.vertx_web.services.UserService;
import com.github.kaivu.vertx_web.web.rests.CommonRouter;
import com.github.kaivu.vertx_web.web.rests.ProductRouter;
import com.github.kaivu.vertx_web.web.rests.UserRouter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

public class AppModule extends AbstractModule {
    private final Vertx vertx;

    public AppModule(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    protected void configure() {
        // Bind your services and repositories here
    }

    @Provides
    @Singleton
    Vertx provideVertx() {
        return vertx;
    }

    @Provides
    @Singleton
    Router provideRouter(Vertx vertx) {
        return Router.router(vertx);
    }

    @Provides
    @Singleton
    UserService provideUserService(Vertx vertx) {
        return new UserService(vertx);
    }

    @Provides
    @Singleton
    ProductService provideProductService(Vertx vertx) {
        return new ProductService(vertx);
    }

    @Provides
    @Singleton
    CommonRouter provideCommonRouter(Vertx vertx) {
        return new CommonRouter(vertx);
    }

    @Provides
    @Singleton
    UserRouter provideUserRouter(Vertx vertx, UserService userService) {
        return new UserRouter(vertx, userService);
    }

    @Provides
    @Singleton
    ProductRouter provideProductRouter(Vertx vertx, ProductService productService) {
        return new ProductRouter(vertx, productService);
    }
}
