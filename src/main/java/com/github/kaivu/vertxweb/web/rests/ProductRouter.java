package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.*;
import com.github.kaivu.vertxweb.context.ContextAwareVertxWrapper;
import com.github.kaivu.vertxweb.observability.metrics.MetricsFacade;
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
    private static final String CONTENT_TYPE = HttpConstants.CONTENT_TYPE_JSON;
    private static final int HTTP_OK = HttpStatusCodes.OK;

    @Getter
    private final Router router;

    private final ApplicationConfig appConfig;
    private final ProductService productService;
    private final RouterHelper routerHelper;
    private final MetricsFacade metricsFacade;

    @Inject
    public ProductRouter(
            Vertx vertx,
            ApplicationConfig appConfig,
            ProductService productService,
            RouterHelper routerHelper,
            MetricsFacade metricsFacade) {
        this.router = Router.router(vertx);
        this.appConfig = appConfig;
        this.productService = productService;
        this.routerHelper = routerHelper;
        this.metricsFacade = metricsFacade;
        setupRoutes(appConfig);
    }

    private void setupRoutes(ApplicationConfig appConfig) {
        // Add BodyHandler to parse request bodies
        router.route()
                .handler(BodyHandler.create().setBodyLimit(appConfig.server().maxBodySizeBytes()));

        // API routes using clean async pattern
        router.get().handler(ctx -> RouterHelper.handleAsync(ctx, this::getAllProducts));
        router.get(ProductConstants.ROUTE_BY_ID).handler(ctx -> RouterHelper.handleAsync(ctx, this::getProductById));
        router.post().handler(ctx -> RouterHelper.handleAsync(ctx, this::createProduct));
        router.put(ProductConstants.ROUTE_STOCK_BY_ID)
                .handler(ctx -> RouterHelper.handleAsync(ctx, this::updateProductStock));

        // Context-aware analytics and batch operations
        router.get(ProductConstants.ROUTE_ANALYTICS_REPORT).handler(this::generateAnalyticsReport);
        router.post(ProductConstants.ROUTE_BATCH_OPERATION).handler(this::processBatchOperation);
    }

    private Uni<Void> getAllProducts(RoutingContext ctx) {
        return productService
                .getAllProductsWithContext(ctx)
                .onItem()
                .invoke(products -> RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, products))
                .replaceWithVoid();
    }

    private Uni<Void> getProductById(RoutingContext ctx) {
        // Validate path parameter using RouterHelper
        String productId = routerHelper.validatePathParam(ctx, ProductConstants.PARAM_PRODUCT_ID);

        return productService
                .getProductByIdWithContext(productId, ctx)
                .onItem()
                .invoke(product -> RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, product))
                .replaceWithVoid();
    }

    private Uni<Void> createProduct(RoutingContext ctx) {
        // Validate request body using RouterHelper
        JsonObject body = routerHelper.validateRequestBody(ctx);

        // Apply validation rules using RouterHelper
        ValidationResult validation = Validator.Products.create(
                        appConfig.validation().maxNameLength())
                .validate(body);
        routerHelper.handleValidationErrors(validation);

        return productService
                .createProductWithContext(body, ctx)
                .onItem()
                .invoke(newProduct -> {
                    JsonObject response = new JsonObject()
                            .put(JsonKeys.MESSAGE, ProductConstants.MESSAGE_PRODUCT_CREATED)
                            .put(ProductConstants.KEY_PRODUCT, newProduct);
                    RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.CREATED, response);
                })
                .replaceWithVoid();
    }

    private Uni<Void> updateProductStock(RoutingContext ctx) {
        // Validate path parameter using RouterHelper
        String productId = routerHelper.validatePathParam(ctx, ProductConstants.PARAM_PRODUCT_ID);

        // Validate request body using RouterHelper
        JsonObject body = routerHelper.validateRequestBody(ctx);

        // Apply validation rules using RouterHelper
        ValidationResult validation = Validator.Products.STOCK_UPDATE.validate(body);
        routerHelper.handleValidationErrors(validation);

        // Extract quantity and handle service response
        int newQuantity = body.getInteger(ProductConstants.KEY_QUANTITY);
        return productService
                .updateProductStockWithContext(productId, newQuantity, ctx)
                .onItem()
                .invoke(updatedProduct -> {
                    JsonObject response = new JsonObject()
                            .put(JsonKeys.MESSAGE, ProductConstants.MESSAGE_PRODUCT_STOCK_UPDATED)
                            .put(ProductConstants.KEY_PRODUCT, updatedProduct);
                    RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, response);
                })
                .replaceWithVoid();
    }

    private void generateAnalyticsReport(RoutingContext ctx) {
        // Create context-aware wrapper from HTTP request
        ContextAwareVertxWrapper wrapper = ContextAwareVertxWrapper.fromHttpRequest(ctx.vertx(), ctx);

        // Extract user context if available (from JWT, session, etc.)
        String userId = ctx.user() != null ? ctx.user().principal().getString("sub") : null;
        String tenantId = ctx.request().getHeader(HttpConstants.X_TENANT_ID);

        // Enrich correlation context
        wrapper.getCorrelationContext().withUserId(userId).withTenantId(tenantId);

        wrapper.logEvent("analytics_request_received", "endpoint", ProductConstants.ROUTE_ANALYTICS_REPORT);

        // Prepare request data
        JsonObject requestData = new JsonObject()
                .put(ProductConstants.KEY_REPORT_TYPE, ProductConstants.VALUE_REPORT_TYPE_ANALYTICS)
                .put(JsonKeys.TIMESTAMP, System.currentTimeMillis());

        // Enrich with correlation context for EventBus
        wrapper.enrichEventBusMessage(requestData);

        String analyticsAddress = appConfig.analytics().eventAddress();
        long startedAt = System.currentTimeMillis();
        ctx.vertx().eventBus().request(analyticsAddress, requestData, reply -> {
            if (reply.succeeded()) {
                metricsFacade.recordEventBusRequest(
                        analyticsAddress, OutcomeConstants.SUCCESS, System.currentTimeMillis() - startedAt);
                JsonObject report = new JsonObject(reply.result().body().toString());

                wrapper.logEvent(
                        "analytics_response_success",
                        "duration_ms",
                        wrapper.getCorrelationContext().getProcessingDurationMs(),
                        "correlation_id",
                        wrapper.getCorrelationContext().getCorrelationId());

                sendJsonResponse(ctx, HTTP_OK, report);
            } else {
                metricsFacade.recordEventBusRequest(
                        analyticsAddress, OutcomeConstants.ERROR, System.currentTimeMillis() - startedAt);
                // Extract status code from ServiceException if available
                int statusCode = HttpStatusCodes.INTERNAL_SERVER_ERROR; // default
                String errorMessage = ProductConstants.MESSAGE_ANALYTICS_FAILED;

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
                        .put(JsonKeys.ERROR, errorMessage)
                        .put(JsonKeys.STATUS, OutcomeConstants.ERROR)
                        .put(JsonKeys.STATUS_CODE, statusCode)
                        .put(
                                JsonKeys.CORRELATION_ID,
                                wrapper.getCorrelationContext().getCorrelationId())
                        .put(JsonKeys.TIMESTAMP, System.currentTimeMillis());

                sendJsonResponse(ctx, statusCode, errorResponse);
            }
        });
    }

    private void processBatchOperation(RoutingContext ctx) {
        // Create context-aware wrapper from HTTP request
        ContextAwareVertxWrapper wrapper = ContextAwareVertxWrapper.fromHttpRequest(ctx.vertx(), ctx);
        String operation = ctx.pathParam(ProductConstants.PARAM_OPERATION);

        // Extract user context if available
        String userId = ctx.user() != null ? ctx.user().principal().getString("sub") : null;
        String tenantId = ctx.request().getHeader(HttpConstants.X_TENANT_ID);

        // Enrich correlation context
        wrapper.getCorrelationContext().withUserId(userId).withTenantId(tenantId);

        wrapper.logEvent("batch_operation_request_received", JsonKeys.OPERATION, operation);

        JsonObject requestData =
                new JsonObject().put(JsonKeys.OPERATION, operation).put(JsonKeys.TIMESTAMP, System.currentTimeMillis());

        // Add confirmDelete for delete operations
        if (ProductConstants.VALUE_OPERATION_DELETE.equalsIgnoreCase(operation)) {
            String confirm = ctx.request().getParam(ProductConstants.QUERY_CONFIRM);
            requestData.put(ProductConstants.KEY_CONFIRM_DELETE, ProductConstants.VALUE_CONFIRM_TRUE.equals(confirm));
        }

        // Enrich with correlation context for EventBus
        wrapper.enrichEventBusMessage(requestData);

        String batchOperationAddress = appConfig.eventBus().batchOperationAddress();
        long startedAt = System.currentTimeMillis();
        ctx.vertx().eventBus().request(batchOperationAddress, requestData, reply -> {
            if (reply.succeeded()) {
                metricsFacade.recordEventBusRequest(
                        batchOperationAddress, OutcomeConstants.SUCCESS, System.currentTimeMillis() - startedAt);
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
                metricsFacade.recordEventBusRequest(
                        batchOperationAddress, OutcomeConstants.ERROR, System.currentTimeMillis() - startedAt);
                // Extract status code from ServiceException
                int statusCode = HttpStatusCodes.INTERNAL_SERVER_ERROR;
                String errorMessage = ProductConstants.MESSAGE_BATCH_FAILED;

                if (reply.cause() instanceof io.vertx.core.eventbus.ReplyException) {
                    io.vertx.core.eventbus.ReplyException replyEx =
                            (io.vertx.core.eventbus.ReplyException) reply.cause();
                    statusCode = replyEx.failureCode();
                    errorMessage = replyEx.getMessage();
                }

                wrapper.logEvent(
                        "batch_operation_response_error",
                        JsonKeys.OPERATION,
                        operation,
                        JsonKeys.ERROR,
                        errorMessage,
                        "status_code",
                        statusCode,
                        "correlation_id",
                        wrapper.getCorrelationContext().getCorrelationId());

                JsonObject errorResponse = new JsonObject()
                        .put(JsonKeys.ERROR, errorMessage)
                        .put(JsonKeys.STATUS, OutcomeConstants.ERROR)
                        .put(JsonKeys.OPERATION, operation)
                        .put(JsonKeys.STATUS_CODE, statusCode)
                        .put(
                                JsonKeys.CORRELATION_ID,
                                wrapper.getCorrelationContext().getCorrelationId())
                        .put(JsonKeys.TIMESTAMP, System.currentTimeMillis());

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
