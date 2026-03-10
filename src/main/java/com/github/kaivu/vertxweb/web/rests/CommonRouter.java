package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.constants.HttpStatusCodes;
import com.github.kaivu.vertxweb.web.RouterHelper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.Getter;

@Singleton
public class CommonRouter {
    @Getter
    private final Router router;

    @Inject
    public CommonRouter(final Vertx vertx) {
        this.router = Router.router(vertx);
        initializeRoutes();
    }

    private void initializeRoutes() {
        router.get().handler(ctx -> RouterHelper.handleAsync(ctx, this::publicHandler));
    }

    private Uni<Void> publicHandler(RoutingContext ctx) {
        JsonObject response = new JsonObject()
                .put("message", "This is a public endpoint, no authentication required.")
                .put("status", "success")
                .put("timestamp", System.currentTimeMillis());

        RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, response);
        return Uni.createFrom().voidItem();
    }
}
