package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.services.ProductService;
import com.github.kaivu.vertxweb.web.ResponseHelper;
import com.github.kaivu.vertxweb.web.RouterHelper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.Router;
import io.vertx.mutiny.ext.web.RoutingContext;
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
        router.get().handler(ctx -> RouterHelper.handleAsync(ctx, this::getAllProducts));
        router.get("/:productId").handler(ctx -> RouterHelper.handleAsync(ctx, this::getProductById));
    }

    private Uni<Void> getAllProducts(RoutingContext ctx) {
        return productService
                .getAllProducts()
                .onItem()
                .transformToUni(products -> ResponseHelper.ok(ctx, products))
                .onFailure()
                .recoverWithUni(t -> ResponseHelper.internalError(ctx, "Failed to fetch products"));
    }

    private Uni<Void> getProductById(RoutingContext ctx) {
        String productId = ctx.pathParam("productId");
        if (productId == null || productId.isEmpty()) {
            return ResponseHelper.badRequest(ctx, "Product ID is required");
        }
        return productService
                .getProductById(productId)
                .onItem()
                .transformToUni(product -> {
                    if (product == null) {
                        return ResponseHelper.notFound(ctx, "Product not found");
                    }
                    return ResponseHelper.ok(ctx, product);
                })
                .onFailure()
                .recoverWithUni(t -> ResponseHelper.internalError(ctx, "Failed to fetch product"));
    }
}
