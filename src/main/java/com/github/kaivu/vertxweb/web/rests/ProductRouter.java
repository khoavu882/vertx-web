package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.context.ContextAwareVertxWrapper;
import com.github.kaivu.vertxweb.services.ProductService;
import com.github.kaivu.vertxweb.web.RouterHelper;
import com.github.kaivu.vertxweb.web.validation.ValidationResult;
import com.github.kaivu.vertxweb.web.validation.Validator;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.Getter;

@Singleton
public class ProductRouter {
    private static final String CONTENT_TYPE = AppConstants.Http.CONTENT_TYPE_JSON;
    private static final int HTTP_OK = AppConstants.Status.OK;

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

        // Context-aware analytics and batch operations
        router.get("/analytics/report").handler(this::generateAnalyticsReport);
        router.post("/batch/:operation").handler(this::processBatchOperation);
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

    private void generateAnalyticsReport(RoutingContext ctx) {
        // Create context-aware wrapper from HTTP request
        ContextAwareVertxWrapper wrapper = ContextAwareVertxWrapper.fromHttpRequest(ctx.vertx(), ctx);

        // Extract user context if available (from JWT, session, etc.)
        String userId = ctx.user() != null ? ctx.user().principal().getString("sub") : null;
        String tenantId = ctx.request().getHeader("X-Tenant-ID");

        // Enrich correlation context
        wrapper.getCorrelationContext().withUserId(userId).withTenantId(tenantId);

        wrapper.logEvent("analytics_request_received", "endpoint", "/analytics/report");

        // Prepare request data
        JsonObject requestData =
                new JsonObject().put("reportType", "analytics").put("timestamp", System.currentTimeMillis());

        // Enrich with correlation context for EventBus
        wrapper.enrichEventBusMessage(requestData);

        ctx.vertx().eventBus().request("app.worker.analytics-report", requestData, reply -> {
            if (reply.succeeded()) {
                JsonObject report = new JsonObject(reply.result().body().toString());

                wrapper.logEvent(
                        "analytics_response_success",
                        "duration_ms",
                        wrapper.getCorrelationContext().getProcessingDurationMs(),
                        "correlation_id",
                        wrapper.getCorrelationContext().getCorrelationId());

                sendJsonResponse(ctx, HTTP_OK, report);
            } else {
                // Extract status code from ServiceException if available
                int statusCode = 500; // default
                String errorMessage = "Failed to generate analytics report";

                if (reply.cause() instanceof io.vertx.core.eventbus.ReplyException) {
                    io.vertx.core.eventbus.ReplyException replyEx =
                            (io.vertx.core.eventbus.ReplyException) reply.cause();
                    statusCode = replyEx.failureCode();
                    errorMessage = replyEx.getMessage();
                }

                wrapper.logEvent(
                        "analytics_response_error",
                        "error",
                        errorMessage,
                        "status_code",
                        statusCode,
                        "correlation_id",
                        wrapper.getCorrelationContext().getCorrelationId());

                JsonObject errorResponse = new JsonObject()
                        .put("error", errorMessage)
                        .put("status", "error")
                        .put("statusCode", statusCode)
                        .put("correlationId", wrapper.getCorrelationContext().getCorrelationId())
                        .put("timestamp", System.currentTimeMillis());

                sendJsonResponse(ctx, statusCode, errorResponse);
            }
        });
    }

    private void processBatchOperation(RoutingContext ctx) {
        // Create context-aware wrapper from HTTP request
        ContextAwareVertxWrapper wrapper = ContextAwareVertxWrapper.fromHttpRequest(ctx.vertx(), ctx);
        String operation = ctx.pathParam("operation");

        // Extract user context if available
        String userId = ctx.user() != null ? ctx.user().principal().getString("sub") : null;
        String tenantId = ctx.request().getHeader("X-Tenant-ID");

        // Enrich correlation context
        wrapper.getCorrelationContext().withUserId(userId).withTenantId(tenantId);

        wrapper.logEvent("batch_operation_request_received", "operation", operation);

        JsonObject requestData =
                new JsonObject().put("operation", operation).put("timestamp", System.currentTimeMillis());

        // Add confirmDelete for delete operations
        if ("delete".equalsIgnoreCase(operation)) {
            String confirm = ctx.request().getParam("confirm");
            requestData.put("confirmDelete", "true".equals(confirm));
        }

        // Enrich with correlation context for EventBus
        wrapper.enrichEventBusMessage(requestData);

        ctx.vertx().eventBus().request("app.worker.batch-operation", requestData, reply -> {
            if (reply.succeeded()) {
                JsonObject result = new JsonObject(reply.result().body().toString());

                wrapper.logEvent(
                        "batch_operation_response_success",
                        "operation",
                        operation,
                        "duration_ms",
                        wrapper.getCorrelationContext().getProcessingDurationMs(),
                        "correlation_id",
                        wrapper.getCorrelationContext().getCorrelationId());

                sendJsonResponse(ctx, HTTP_OK, result);
            } else {
                // Extract status code from ServiceException
                int statusCode = 500;
                String errorMessage = "Batch operation failed";

                if (reply.cause() instanceof io.vertx.core.eventbus.ReplyException) {
                    io.vertx.core.eventbus.ReplyException replyEx =
                            (io.vertx.core.eventbus.ReplyException) reply.cause();
                    statusCode = replyEx.failureCode();
                    errorMessage = replyEx.getMessage();
                }

                wrapper.logEvent(
                        "batch_operation_response_error",
                        "operation",
                        operation,
                        "error",
                        errorMessage,
                        "status_code",
                        statusCode,
                        "correlation_id",
                        wrapper.getCorrelationContext().getCorrelationId());

                JsonObject errorResponse = new JsonObject()
                        .put("error", errorMessage)
                        .put("status", "error")
                        .put("operation", operation)
                        .put("statusCode", statusCode)
                        .put("correlationId", wrapper.getCorrelationContext().getCorrelationId())
                        .put("timestamp", System.currentTimeMillis());

                sendJsonResponse(ctx, statusCode, errorResponse);
            }
        });
    }

    private void sendJsonResponse(RoutingContext ctx, int statusCode, JsonObject response) {
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE)
                .end(response.encode());
    }
}
