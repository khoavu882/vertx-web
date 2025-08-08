package com.github.kaivu.vertxweb.middlewares;

import com.github.kaivu.vertxweb.constants.AppConstants;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Khoa Vu.
 * Mail: khoavd12@fpt.com
 * Date: 9/12/24
 * Time: 9:36 AM
 */
public class AuthHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);

    private AuthHandler() {
        // Private constructor to prevent instantiation
    }

    public static void authenticateRequest(RoutingContext ctx) {

        String path = ctx.request().path();
        log.info("Authenticating request: {}", path);

        // Allow configured public endpoints to bypass authentication
        // TODO: This should be injected via DI for better testability
        // For now, we use the configured public paths from application.properties
        if (path.startsWith("/api/common")) {
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
