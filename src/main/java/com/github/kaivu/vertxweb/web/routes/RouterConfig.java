package com.github.kaivu.vertxweb.web.routes;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.HttpConstants;
import com.github.kaivu.vertxweb.constants.PathConstants;
import com.github.kaivu.vertxweb.middlewares.AuthHandler;
import com.github.kaivu.vertxweb.middlewares.ErrorHandler;
import com.github.kaivu.vertxweb.middlewares.LoggingHandler;
import com.github.kaivu.vertxweb.observability.tracing.TracingService;
import com.github.kaivu.vertxweb.web.AppRouter;
import com.github.kaivu.vertxweb.web.rests.CommonRouter;
import com.github.kaivu.vertxweb.web.rests.HealthRouter;
import com.github.kaivu.vertxweb.web.rests.MetricsRouter;
import com.github.kaivu.vertxweb.web.rests.OpenApiRouter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
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

/**
 * Wires the HTTP middleware pipeline and mounts all sub-routers.
 *
 * <p>Infrastructure routers (health, metrics, openapi, common) are injected individually because
 * they have special mounting semantics (public paths, no auth). Domain routers are injected as a
 * {@code Set<AppRouter>} via Guice Multibinder — adding a new domain router requires only one
 * binding in AppModule; this class never needs to change.
 */
@Singleton
public class RouterConfig {

    private static final Logger log = LoggerFactory.getLogger(RouterConfig.class);
    private static final Set<String> ALLOWED_HEADERS = new HashSet<>(Arrays.asList(
            HttpHeaders.CONTENT_TYPE.toString(),
            HttpConstants.AUTHORIZATION,
            HttpConstants.X_TENANT_ID,
            HttpConstants.X_CORRELATION_ID,
            TracingService.HEADER_TRACE_ID,
            HttpConstants.TRACEPARENT,
            HttpConstants.TRACESTATE));
    private static final Set<HttpMethod> ALLOWED_METHODS = new HashSet<>(
            Arrays.asList(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS));

    private final ApplicationConfig appConfig;
    private final LoggingHandler loggingHandler;
    private final AuthHandler authHandler;
    private final ErrorHandler errorHandler;

    @Getter
    private final Router router;

    // Infrastructure routers — special mounting rules, not auto-registered
    private final CommonRouter commonRouter;
    private final HealthRouter healthRouter;
    private final MetricsRouter metricsRouter;
    private final OpenApiRouter openApiRouter;

    // Domain routers — auto-registered via Multibinder; RouterConfig never changes when adding new domains
    private final Set<AppRouter> domainRouters;

    @Inject
    public RouterConfig(
            Router router,
            ApplicationConfig appConfig,
            LoggingHandler loggingHandler,
            AuthHandler authHandler,
            ErrorHandler errorHandler,
            CommonRouter commonRouter,
            HealthRouter healthRouter,
            MetricsRouter metricsRouter,
            OpenApiRouter openApiRouter,
            Set<AppRouter> domainRouters) {
        this.appConfig = appConfig;
        this.loggingHandler = loggingHandler;
        this.authHandler = authHandler;
        this.errorHandler = errorHandler;
        this.router = router;
        this.commonRouter = commonRouter;
        this.healthRouter = healthRouter;
        this.metricsRouter = metricsRouter;
        this.openApiRouter = openApiRouter;
        this.domainRouters = domainRouters;

        router.route().handler(TimeoutHandler.create(appConfig.server().requestTimeoutMs()));
        setupCors();
        router.route().handler(loggingHandler::logRequest);
        router.route().handler(authHandler::authenticateRequest);
        setupRoutes();

        router.route().failureHandler(errorHandler::handle);
    }

    private void setupRoutes() {
        String apiPrefix = appConfig.server().apiPrefix();

        // Public routes
        router.route(apiPrefix + PathConstants.COMMON_ROOT + PathConstants.ANY_ROOT)
                .subRouter(commonRouter.getRouter());

        // Infrastructure routes (public) — fixed set, no auth required
        router.route(PathConstants.ANY_ROOT).subRouter(healthRouter.getRouter());
        router.route(PathConstants.ANY_ROOT).subRouter(metricsRouter.getRouter());
        router.route(PathConstants.ANY_ROOT).subRouter(openApiRouter.getRouter());

        // Domain routes — auto-registered from Multibinder set
        // Public domain routers (isProtected() == false) are mounted before auth;
        // protected ones are mounted after. The middleware pipeline above already enforces auth
        // via authHandler on all routes; individual routers may opt out via isProtected().
        for (AppRouter domainRouter : domainRouters) {
            router.route(domainRouter.getMountPath()).subRouter(domainRouter.getRouter());
            log.debug("Mounted domain router at: {}", domainRouter.getMountPath());
        }

        log.info("RouterConfig initialized with {} domain routers, API prefix: {}", domainRouters.size(), apiPrefix);
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
            if (normalized.isEmpty()) continue;
            if (!normalized.equals("*") && !normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                log.warn("CORS: suspicious origin value '{}' — expected http/https URI or '*'", normalized);
            }
            corsHandler.addOrigin(normalized);
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
