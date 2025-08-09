package com.github.kaivu.vertxweb.middlewares;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.AppConstants;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.http.HttpHeaders;
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
    private final ApplicationConfig applicationConfig;

    @Inject
    public AuthHandler(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
    }

    public void authenticateRequest(RoutingContext ctx) {

        String path = ctx.request().path();
        log.info("Authenticating request: {}", path);

        // Allow configured public endpoints to bypass authentication
        String publicPaths = applicationConfig.security().publicPaths();
        if (path.startsWith(publicPaths)) {
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

        // TODO: Implement actual token validation here
        // For now, just check if Bearer token exists
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
}
