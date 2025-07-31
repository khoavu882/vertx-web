package com.github.kaivu.vertx_web.web.routes;

import com.github.kaivu.vertx_web.middlewares.AuthHandler;
import com.github.kaivu.vertx_web.middlewares.ErrorHandler;
import com.github.kaivu.vertx_web.middlewares.LoggingHandler;
import com.github.kaivu.vertx_web.web.rests.CommonRouter;
import com.github.kaivu.vertx_web.web.rests.ProductRouter;
import com.github.kaivu.vertx_web.web.rests.UserRouter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.Getter;

@Singleton
public class RouterConfig {
    private final Vertx vertx;

    @Getter
    private final Router router;

    private final CommonRouter commonRouter;
    private final UserRouter userRouter;
    private final ProductRouter productRouter;
    private static final String API_PREFIX = "/api";

    @Inject
    public RouterConfig(
            Vertx vertx, Router router, CommonRouter commonRouter, UserRouter userRouter, ProductRouter productRouter) {
        this.vertx = vertx;
        this.router = router;
        this.commonRouter = commonRouter;
        this.userRouter = userRouter;
        this.productRouter = productRouter;

        setupMiddleware();
        setupRoutes();
    }

    private void setupMiddleware() {
        router.route()
                .handler(BodyHandler.create()
                        .setBodyLimit(1024 * 1024) // 1MB limit
                        .setHandleFileUploads(true)
                        .setUploadsDirectory("uploads"));

        router.route().handler(LoggingHandler::logRequest);

        router.route(API_PREFIX + "/*")
                .handler(AuthHandler::authenticateRequest)
                .handler(ctx -> ctx.response().putHeader("Cache-Control", "no-store"));
    }

    private void setupRoutes() {
        // Public routes (bypassing authentication)
        router.route(API_PREFIX + "/common/*").subRouter(commonRouter.getRouter());

        // Protected routes
        router.route(API_PREFIX + "/users/*").subRouter(userRouter.getRouter());
        router.route(API_PREFIX + "/products/*").subRouter(productRouter.getRouter());

        // Global error handling
        router.route().last().failureHandler(ErrorHandler::handle);
    }
}
