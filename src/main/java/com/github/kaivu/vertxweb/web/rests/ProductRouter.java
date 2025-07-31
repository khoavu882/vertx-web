package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.services.ProductService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.Getter;

@Singleton
public class ProductRouter {
    private static final String CONTENT_TYPE = AppConstants.Http.CONTENT_TYPE_JSON;

    private static final int HTTP_OK = AppConstants.Status.OK;
    private static final int HTTP_NOT_FOUND = AppConstants.Status.NOT_FOUND;

    @Getter
    private final Router router;

    private final ProductService productService;

    @Inject
    public ProductRouter(Vertx vertx, ProductService productService) {
        this.router = Router.router(vertx);
        this.productService = productService;
        setupRoutes();
    }

    private void setupRoutes() {
        router.get().handler(this::getAllProducts);
        router.get("/:productId").handler(this::getProductById);
    }

    private void getAllProducts(RoutingContext ctx) {
        productService
                .getAllProducts()
                .subscribe()
                .with(products -> sendJsonResponse(ctx, HTTP_OK, products), ctx::fail);
    }

    private void getProductById(RoutingContext ctx) {
        String productId = ctx.pathParam("productId");
        if (productId == null || productId.isEmpty()) {
            JsonObject errorResponse =
                    new JsonObject().put("error", "Product ID is required").put("status", "error");
            sendJsonResponse(ctx, HTTP_NOT_FOUND, errorResponse);
            return;
        }
        productService
                .getProductById(productId)
                .subscribe()
                .with(product -> sendJsonResponse(ctx, HTTP_OK, product), ctx::fail);
    }

    private void sendJsonResponse(RoutingContext ctx, int statusCode, JsonObject response) {
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE)
                .end(response.encode());
    }
}
