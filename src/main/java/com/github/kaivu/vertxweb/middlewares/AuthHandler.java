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

        if (("/api/common").startsWith(ctx.request().path())) {
            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, AppConstants.Http.CONTENT_TYPE_JSON)
                    .setStatusCode(AppConstants.Status.OK)
                    .end(new JsonObject()
                            .put("message", "Common endpoint accessed")
                            .encode());
        }

        String authHeader = ctx.request().getHeader(HttpHeaders.AUTHORIZATION.toString());

        if (authHeader == null) {
            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, AppConstants.Http.CONTENT_TYPE_JSON)
                    .setStatusCode(AppConstants.Status.UNAUTHORIZED)
                    .end();
        }

        ctx.next();
    }
}
