package com.github.kaivu.vertxweb.middlewares;

import com.github.kaivu.vertxweb.web.ResponseHelper;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.http.HttpHeaders;
import io.vertx.mutiny.ext.web.RoutingContext;
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

        if ("/api/common".startsWith(ctx.request().path())) {
            ResponseHelper.ok(ctx, new JsonObject().put("message", "Common endpoint accessed"));
            return;
        }

        String authHeader = ctx.request().getHeader(HttpHeaders.AUTHORIZATION.toString());

        if (authHeader == null) {
            ResponseHelper.badRequest(ctx, "Missing Authorization header");
            return;
        }

        ctx.next();
    }
}
