package com.github.kaivu.vertxweb.web.routes;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.middlewares.AuthHandler;
import com.github.kaivu.vertxweb.middlewares.ErrorHandler;
import com.github.kaivu.vertxweb.middlewares.LoggingHandler;
import com.github.kaivu.vertxweb.web.rests.CommonRouter;
import com.github.kaivu.vertxweb.web.rests.ProductRouter;
import com.github.kaivu.vertxweb.web.rests.UserRouter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RouterConfig {

    private static final Logger log = LoggerFactory.getLogger(RouterConfig.class);

    private final Vertx vertx;
    private final ApplicationConfig appConfig;

    @Getter
    private final Router router;

    private final CommonRouter commonRouter;
    private final UserRouter userRouter;
    private final ProductRouter productRouter;

    @Inject
    public RouterConfig(
            Vertx vertx,
            Router router,
            ApplicationConfig appConfig,
            CommonRouter commonRouter,
            UserRouter userRouter,
            ProductRouter productRouter) {
        this.vertx = vertx;
        this.appConfig = appConfig;
        this.router = router;
        this.commonRouter = commonRouter;
        this.userRouter = userRouter;
        this.productRouter = productRouter;

        // Setup middleware pipeline in correct order
        router.route().handler(LoggingHandler::logRequest);
        router.route().handler(AuthHandler::authenticateRequest);
        setupRoutes();

        // Global error handling
        router.route().failureHandler(ErrorHandler::handle);
    }

    private void setupRoutes() {
        String apiPrefix = appConfig.server().apiPrefix();

        // Public routes (bypassing authentication)
        router.route(apiPrefix + "/common/*").subRouter(commonRouter.getRouter());

        // Protected routes
        router.route(apiPrefix + "/users/*").subRouter(userRouter.getRouter());
        router.route(apiPrefix + "/products/*").subRouter(productRouter.getRouter());

        log.info(
                "RouterConfig initialized with API prefix: {}",
                appConfig.server().apiPrefix());
    }
}
