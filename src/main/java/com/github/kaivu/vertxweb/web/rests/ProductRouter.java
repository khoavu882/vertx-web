package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.services.ProductService;
import com.github.kaivu.vertxweb.web.RouterHelper;
import com.github.kaivu.vertxweb.web.validation.ValidationResult;
import com.github.kaivu.vertxweb.web.validation.Validator;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.Getter;

@Singleton
public class ProductRouter {

    @Getter
    private final Router router;

    private final ProductService productService;
    private final RouterHelper routerHelper;

    @Inject
    public ProductRouter(Vertx vertx, ProductService productService, RouterHelper routerHelper) {
        this.router = Router.router(vertx);
        this.productService = productService;
        this.routerHelper = routerHelper;
        setupRoutes();
    }

    private void setupRoutes() {
        // Add BodyHandler to parse request bodies
        router.route().handler(BodyHandler.create());

        // API routes using clean async pattern
        router.get().handler(ctx -> RouterHelper.handleAsync(ctx, this::getAllProducts));
        router.get("/:productId").handler(ctx -> RouterHelper.handleAsync(ctx, this::getProductById));
        router.post().handler(ctx -> RouterHelper.handleAsync(ctx, this::createProduct));
        router.put("/:productId/stock").handler(ctx -> RouterHelper.handleAsync(ctx, this::updateProductStock));
    }

    private Uni<Void> getAllProducts(RoutingContext ctx) {
        return productService
                .getAllProducts()
                .onItem()
                .invoke(products -> RouterHelper.sendJsonResponse(ctx, AppConstants.Status.OK, products))
                .replaceWithVoid();
    }

    private Uni<Void> getProductById(RoutingContext ctx) {
        // Validate path parameter using RouterHelper
        String productId = routerHelper.validatePathParam(ctx, "productId");

        return productService
                .getProductById(productId)
                .onItem()
                .invoke(product -> RouterHelper.sendJsonResponse(ctx, AppConstants.Status.OK, product))
                .replaceWithVoid();
    }

    private Uni<Void> createProduct(RoutingContext ctx) {
        // Validate request body using RouterHelper
        JsonObject body = routerHelper.validateRequestBody(ctx);

        // Apply validation rules using RouterHelper
        ValidationResult validation = Validator.Products.CREATE.validate(body);
        routerHelper.handleValidationErrors(validation);

        return productService
                .createProduct(body)
                .onItem()
                .invoke(newProduct -> {
                    JsonObject response = new JsonObject()
                            .put("message", "Product created successfully")
                            .put("product", newProduct);
                    RouterHelper.sendJsonResponse(ctx, AppConstants.Status.CREATED, response);
                })
                .replaceWithVoid();
    }

    private Uni<Void> updateProductStock(RoutingContext ctx) {
        // Validate path parameter using RouterHelper
        String productId = routerHelper.validatePathParam(ctx, "productId");

        // Validate request body using RouterHelper
        JsonObject body = routerHelper.validateRequestBody(ctx);

        // Apply validation rules using RouterHelper
        ValidationResult validation = Validator.Products.STOCK_UPDATE.validate(body);
        routerHelper.handleValidationErrors(validation);

        // Extract quantity and handle service response
        int newQuantity = body.getInteger("quantity");
        return productService
                .updateProductStock(productId, newQuantity)
                .onItem()
                .invoke(updatedProduct -> {
                    JsonObject response = new JsonObject()
                            .put("message", "Product stock updated successfully")
                            .put("product", updatedProduct);
                    RouterHelper.sendJsonResponse(ctx, AppConstants.Status.OK, response);
                })
                .replaceWithVoid();
    }
}
