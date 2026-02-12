package com.github.kaivu.vertxweb.web.routes;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.middlewares.AuthHandler;
import com.github.kaivu.vertxweb.middlewares.ErrorHandler;
import com.github.kaivu.vertxweb.middlewares.LoggingHandler;
import com.github.kaivu.vertxweb.web.rests.CommonRouter;
import com.github.kaivu.vertxweb.web.rests.HealthRouter;
import com.github.kaivu.vertxweb.web.rests.MetricsRouter;
import com.github.kaivu.vertxweb.web.rests.ProductRouter;
import com.github.kaivu.vertxweb.web.rests.UserRouter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.TimeoutHandler;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RouterConfig {

    private static final Logger log = LoggerFactory.getLogger(RouterConfig.class);
    private static final Set<String> ALLOWED_HEADERS = new HashSet<>(Arrays.asList(
            HttpHeaders.CONTENT_TYPE.toString(),
            HttpHeaders.AUTHORIZATION.toString(),
            "X-Tenant-ID",
            "X-Correlation-ID"));
    private static final Set<HttpMethod> ALLOWED_METHODS = new HashSet<>(
            Arrays.asList(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS));

    private final Vertx vertx;
    private final ApplicationConfig appConfig;
    private final LoggingHandler loggingHandler;
    private final AuthHandler authHandler;
    private final ErrorHandler errorHandler;

    @Getter
    private final Router router;

    private final CommonRouter commonRouter;
    private final HealthRouter healthRouter;
    private final MetricsRouter metricsRouter;
    private final UserRouter userRouter;
    private final ProductRouter productRouter;

    @Inject
    public RouterConfig(
            Vertx vertx,
            Router router,
            ApplicationConfig appConfig,
            LoggingHandler loggingHandler,
            AuthHandler authHandler,
            ErrorHandler errorHandler,
            CommonRouter commonRouter,
            HealthRouter healthRouter,
            MetricsRouter metricsRouter,
            UserRouter userRouter,
            ProductRouter productRouter) {
        this.vertx = vertx;
        this.appConfig = appConfig;
        this.loggingHandler = loggingHandler;
        this.authHandler = authHandler;
        this.errorHandler = errorHandler;
        this.router = router;
        this.commonRouter = commonRouter;
        this.healthRouter = healthRouter;
        this.metricsRouter = metricsRouter;
        this.userRouter = userRouter;
        this.productRouter = productRouter;

        // Setup middleware pipeline in correct order
        router.route().handler(TimeoutHandler.create(appConfig.server().requestTimeoutMs()));
        setupCors();
        router.route().handler(loggingHandler::logRequest);
        router.route().handler(authHandler::authenticateRequest);
        setupRoutes();

        // Global error handling
        router.route().failureHandler(errorHandler::handle);
    }

    private void setupRoutes() {
        String apiPrefix = appConfig.server().apiPrefix();

        // Public routes (bypassing authentication)
        router.route(apiPrefix + "/common/*").subRouter(commonRouter.getRouter());

        // Health check routes (public, no authentication required)
        healthRouter.configureRoutes(router);
        metricsRouter.configureRoutes(router);

        // Protected routes
        router.route(apiPrefix + "/users/*").subRouter(userRouter.getRouter());
        router.route(apiPrefix + "/products/*").subRouter(productRouter.getRouter());

        log.info(
                "RouterConfig initialized with API prefix: {} and health endpoints",
                appConfig.server().apiPrefix());
    }

    private void setupCors() {
        ApplicationConfig.ServerConfig.CorsConfig corsConfig =
                appConfig.server().cors();
        if (!corsConfig.enable()) {
            log.info("Global CORS is disabled by configuration.");
            return;
        }

        CorsHandler corsHandler =
                CorsHandler.create().allowedHeaders(ALLOWED_HEADERS).allowedMethods(ALLOWED_METHODS);
        for (String origin : corsConfig.allowedOrigins().split(",")) {
            String normalized = origin.trim();
            if (!normalized.isEmpty()) {
                corsHandler.addOrigin(normalized);
            }
        }

        boolean allowCredentials = corsConfig.allowCredentials();
        if (allowCredentials && corsConfig.allowedOrigins().contains("*")) {
            log.warn("CORS credentials requested with wildcard origin. For safety, allowCredentials is disabled.");
            allowCredentials = false;
        }
        corsHandler.allowCredentials(allowCredentials);

        router.route().handler(corsHandler);
    }
}
