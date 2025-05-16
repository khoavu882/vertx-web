package com.github.kaivu.vertx_web.web.routes;

import com.github.kaivu.vertx_web.middlewares.AuthHandler;
import com.github.kaivu.vertx_web.middlewares.LoggingHandler;
import com.github.kaivu.vertx_web.web.errors.ErrorHandler;
import com.github.kaivu.vertx_web.web.rests.CommonRouter;
import com.github.kaivu.vertx_web.web.rests.ProductRouter;
import com.github.kaivu.vertx_web.web.rests.UserRouter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

@Singleton
public class RouterConfig {
    private final Vertx vertx;
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
        // Enable body handling with size limit and uploads directory
        router.route()
                .handler(BodyHandler.create()
                        .setBodyLimit(1024 * 1024) // 1MB limit
                        .setHandleFileUploads(true)
                        .setUploadsDirectory("uploads"));

        // Register logging filters
        router.route().handler(LoggingHandler::logRequest);

        // Global authentication middleware
        router.route(API_PREFIX + "/*")
                .handler(AuthHandler::authenticateRequest)
                .handler(ctx -> ctx.response().putHeader("Cache-Control", "no-store"));
    }

    private void setupRoutes() {
        // Public routes (bypassing authentication)
        router.mountSubRouter(API_PREFIX + "/common", commonRouter.getRouter());

        // Protected routes
        router.mountSubRouter(API_PREFIX + "/users", userRouter.getRouter());
        router.mountSubRouter(API_PREFIX + "/products", productRouter.getRouter());

        // Global error handling
        router.route().last().failureHandler(ErrorHandler::globalErrorHandler);
    }

    public Router getRouter() {
        return router;
    }
}
