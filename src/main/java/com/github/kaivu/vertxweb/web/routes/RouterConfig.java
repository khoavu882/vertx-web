package com.github.kaivu.vertxweb.web.routes;

import com.github.kaivu.vertxweb.middlewares.AuthHandler;
import com.github.kaivu.vertxweb.middlewares.ErrorHandler;
import com.github.kaivu.vertxweb.middlewares.LoggingHandler;
import com.github.kaivu.vertxweb.web.rests.CommonRouter;
import com.github.kaivu.vertxweb.web.rests.ProductRouter;
import com.github.kaivu.vertxweb.web.rests.UserRouter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.Router;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RouterConfig {

    private static final Logger log = LoggerFactory.getLogger(RouterConfig.class);

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

        router.route().handler(AuthHandler::authenticateRequest);
        setupRoutes();
        router.route().handler(LoggingHandler::logRequest);

        log.info("RouterConfig initialized with API prefix: {}", API_PREFIX);
    }

    private void setupRoutes() {
        // Public routes (bypassing authentication)
        router.route(API_PREFIX + "/common/*").subRouter(commonRouter.getRouter());

        // Protected routes
        router.route(API_PREFIX + "/users/*").subRouter(userRouter.getRouter());
        router.route(API_PREFIX + "/products/*").subRouter(productRouter.getRouter());

        // Global error handling
        router.route().failureHandler(ErrorHandler::handle);
    }
}
