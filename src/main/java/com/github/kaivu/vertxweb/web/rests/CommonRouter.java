package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.web.RouterHelper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;

@Singleton
public class CommonRouter {
    private static final Set<String> ALLOWED_HEADERS =
            new HashSet<>(Arrays.asList(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaders.AUTHORIZATION.toString()));
    private static final Set<HttpMethod> ALLOWED_METHODS = new HashSet<>(
            Arrays.asList(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS));

    @Getter
    private final Router router;

    private final RouterHelper routerHelper;

    @Inject
    public CommonRouter(final Vertx vertx, RouterHelper routerHelper) {
        this.router = Router.router(vertx);
        this.routerHelper = routerHelper;
        setupCors();
        initializeRoutes();
    }

    private void setupCors() {
        router.route()
                .handler(CorsHandler.create()
                        .addOrigin("*")
                        .allowedHeaders(ALLOWED_HEADERS)
                        .allowedMethods(ALLOWED_METHODS));
    }

    private void initializeRoutes() {
        router.get().handler(ctx -> RouterHelper.handleAsync(ctx, this::publicHandler));
    }

    private Uni<Void> publicHandler(RoutingContext ctx) {
        JsonObject response = new JsonObject()
                .put("message", "This is a public endpoint, no authentication required.")
                .put("status", "success")
                .put("timestamp", System.currentTimeMillis());

        RouterHelper.sendJsonResponse(ctx, AppConstants.Status.OK, response);
        return Uni.createFrom().voidItem();
    }
}
