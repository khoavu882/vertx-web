package com.github.kaivu.vertxweb.middlewares;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.AppConstants;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Khoa Vu.
 * Mail: kai.vu.dev@gmail.com
 * Date: 9/12/24
 * Time: 9:36 AM
 */
@Singleton
public class AuthHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);
    private static final String AUTH_MODE_HEADER = "X-Auth-Mode";
    private static final String DEMO_AUTH_MODE = "demo-bearer-presence-check";
    private final ApplicationConfig applicationConfig;

    @Inject
    public AuthHandler(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
        if (applicationConfig.security().enableAuth()) {
            log.warn("Authentication is running in DEMO mode (Bearer token presence only). "
                    + "Do not use this implementation in production.");
        } else {
            log.warn("Authentication is disabled via configuration (app.security.enable-auth=false).");
        }
    }

    public void authenticateRequest(RoutingContext ctx) {
        if (!applicationConfig.security().enableAuth()) {
            ctx.next();
            return;
        }

        if (ctx.request().method() == HttpMethod.OPTIONS) {
            ctx.next();
            return;
        }

        String path = ctx.request().path();
        log.info("Authenticating request: {}", path);
        ctx.response().putHeader(AUTH_MODE_HEADER, DEMO_AUTH_MODE);

        // Allow configured public endpoints and health endpoints to bypass authentication.
        if (isPublicEndpoint(path)) {
            log.info("Bypassing authentication for public endpoint: {}", path);
            ctx.next();
            return;
        }

        String authHeader = ctx.request().getHeader(HttpHeaders.AUTHORIZATION.toString());

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Unauthorized request to protected endpoint: {}", path);
            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, AppConstants.Http.CONTENT_TYPE_JSON)
                    .setStatusCode(AppConstants.Status.UNAUTHORIZED)
                    .end(new JsonObject()
                            .put("error", "Unauthorized")
                            .put("message", "Missing or invalid authorization header")
                            .encode());
            return;
        }

        // DEMO ONLY: this intentionally checks only Bearer token presence.
        // Replace with real JWT validation before any non-demo deployment.
        String token = authHeader.substring(7); // Remove "Bearer " prefix
        if (token.isEmpty()) {
            log.warn("Empty token for protected endpoint: {}", path);
            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, AppConstants.Http.CONTENT_TYPE_JSON)
                    .setStatusCode(AppConstants.Status.UNAUTHORIZED)
                    .end(new JsonObject()
                            .put("error", "Unauthorized")
                            .put("message", "Empty authorization token")
                            .encode());
            return;
        }

        log.info("Authentication successful for: {}", path);
        ctx.next();
    }

    private boolean isPublicEndpoint(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        // Health probes should remain reachable for local/dev tooling.
        if (path.startsWith("/health")) {
            return true;
        }

        String publicPaths = applicationConfig.security().publicPaths();
        if (publicPaths == null || publicPaths.isBlank()) {
            return isOpenMetricsEndpoint(path);
        }

        for (String publicPath : publicPaths.split(",")) {
            String normalizedPath = publicPath.trim();
            if (!normalizedPath.isEmpty() && path.startsWith(normalizedPath)) {
                return true;
            }
        }

        return isOpenMetricsEndpoint(path);
    }

    private boolean isOpenMetricsEndpoint(String path) {
        ApplicationConfig.ObservabilityConfig.MetricsConfig metricsConfig =
                applicationConfig.observability().metrics();
        if (!metricsConfig.enable() || !"open".equalsIgnoreCase(metricsConfig.exposure())) {
            return false;
        }

        String endpointPath = metricsConfig.endpointPath();
        if (endpointPath == null || endpointPath.isBlank()) {
            endpointPath = "/metrics";
        } else if (!endpointPath.startsWith("/")) {
            endpointPath = "/" + endpointPath;
        }
        return path.startsWith(endpointPath);
    }
}
