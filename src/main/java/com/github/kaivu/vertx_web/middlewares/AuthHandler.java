package com.github.kaivu.vertx_web.middlewares;

import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

/**
 * Created by Khoa Vu.
 * Mail: khoavd12@fpt.com
 * Date: 9/12/24
 * Time: 9:36 AM
 */
public class AuthHandler {
    private static final String VALID_TOKEN = "valid_token";
    private static final String AUTH_SCHEME = "Bearer ";

    public static void authenticateRequest(RoutingContext ctx) {
        if ("/common".startsWith(ctx.request().path())) {
            // Skip authentication for public routes
            ctx.next();
        } else {
            String authHeader = ctx.request().getHeader(HttpHeaders.AUTHORIZATION.toString());

            if (authHeader != null && authHeader.startsWith(AUTH_SCHEME)) {
                String token = authHeader.substring(AUTH_SCHEME.length());
                if (VALID_TOKEN.equals(token)) {
                    ctx.next();
                } else {
                    ctx.response().setStatusCode(401).end("Unauthorized: Invalid token");
                }
            } else {
                ctx.response().setStatusCode(401).end("Unauthorized: Missing or invalid authorization header");
            }
        }
    }
}
