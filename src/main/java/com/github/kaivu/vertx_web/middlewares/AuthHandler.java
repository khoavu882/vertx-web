package com.github.kaivu.vertx_web.middlewares;

import com.github.kaivu.vertx_web.constants.AppConstants;
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

        if ("/common".startsWith(ctx.request().path())) {
            ctx.next();
        } else {
            String authHeader = ctx.request().getHeader(HttpHeaders.AUTHORIZATION.toString());

            if (authHeader != null && authHeader.startsWith(AppConstants.Auth.AUTH_SCHEME)) {
                String token = authHeader.substring(AppConstants.Auth.AUTH_SCHEME.length());
                if (AppConstants.Auth.VALID_TOKEN.equals(token)) {
                    ctx.next();
                } else {
                    ctx.response()
                            .putHeader(HttpHeaders.CONTENT_TYPE, AppConstants.Http.CONTENT_TYPE_JSON)
                            .setStatusCode(AppConstants.Status.UNAUTHORIZED)
                            .end(new JsonObject()
                                    .put("message", AppConstants.Messages.INVALID_TOKEN)
                                    .toBuffer());
                }
            } else {
                ctx.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, AppConstants.Http.CONTENT_TYPE_JSON)
                        .setStatusCode(AppConstants.Status.UNAUTHORIZED)
                        .end();
            }
        }
    }
}
