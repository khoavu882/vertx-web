package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.ContextKeys;
import com.github.kaivu.vertxweb.constants.HttpStatusCodes;
import com.github.kaivu.vertxweb.constants.JsonKeys;
import com.github.kaivu.vertxweb.constants.OutcomeConstants;
import com.github.kaivu.vertxweb.constants.PathConstants;
import com.github.kaivu.vertxweb.constants.ProductConstants;
import com.github.kaivu.vertxweb.context.ContextAwareVertxWrapper;
import com.github.kaivu.vertxweb.context.CorrelationContext;
import com.github.kaivu.vertxweb.observability.metrics.MetricsFacade;
import com.github.kaivu.vertxweb.services.ProductService;
import com.github.kaivu.vertxweb.web.AppRouter;
import com.github.kaivu.vertxweb.web.RouterHelper;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.github.kaivu.vertxweb.web.validation.ValidationResult;
import com.github.kaivu.vertxweb.web.validation.Validator;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.Getter;

@Singleton
public class ProductRouter implements AppRouter {

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

    @Override
    public String getMountPath() {
        return appConfig.server().apiPrefix() + PathConstants.PRODUCTS_ROOT + PathConstants.ANY_ROOT;
    }

    private void setupRoutes(ApplicationConfig appConfig) {
        router.route()
                .handler(BodyHandler.create().setBodyLimit(appConfig.server().maxBodySizeBytes()));

        router.get().handler(ctx -> RouterHelper.handleAsync(ctx, this::getAllProducts));
        router.get(ProductConstants.ROUTE_BY_ID).handler(ctx -> RouterHelper.handleAsync(ctx, this::getProductById));
        router.post().handler(ctx -> RouterHelper.handleAsync(ctx, this::createProduct));
        router.put(ProductConstants.ROUTE_STOCK_BY_ID)
                .handler(ctx -> RouterHelper.handleAsync(ctx, this::updateProductStock));

        // Analytics and batch: now use handleAsync so errors route through ErrorHandler
        router.get(ProductConstants.ROUTE_ANALYTICS_REPORT)
                .handler(ctx -> RouterHelper.handleAsync(ctx, this::generateAnalyticsReport));
        router.post(ProductConstants.ROUTE_BATCH_OPERATION)
                .handler(ctx -> RouterHelper.handleAsync(ctx, this::processBatchOperation));
    }

    private Uni<Void> getAllProducts(RoutingContext ctx) {
        return productService
                .getAllProductsWithContext(extractCorrelation(ctx))
                .onItem()
                .invoke(products -> RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, products))
                .replaceWithVoid();
    }

    private Uni<Void> getProductById(RoutingContext ctx) {
        String productId = routerHelper.validatePathParam(ctx, ProductConstants.PARAM_PRODUCT_ID);
        return productService
                .getProductByIdWithContext(productId, extractCorrelation(ctx))
                .onItem()
                .invoke(product -> RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, product))
                .replaceWithVoid();
    }

    private Uni<Void> createProduct(RoutingContext ctx) {
        JsonObject body = routerHelper.validateRequestBody(ctx);
        ValidationResult validation = Validator.Products.create(
                        appConfig.validation().maxNameLength())
                .validate(body);
        routerHelper.handleValidationErrors(validation);

        return productService
                .createProductWithContext(body, extractCorrelation(ctx))
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
        String productId = routerHelper.validatePathParam(ctx, ProductConstants.PARAM_PRODUCT_ID);
        JsonObject body = routerHelper.validateRequestBody(ctx);
        ValidationResult validation = Validator.Products.STOCK_UPDATE.validate(body);
        routerHelper.handleValidationErrors(validation);

        int newQuantity = body.getInteger(ProductConstants.KEY_QUANTITY);
        return productService
                .updateProductStockWithContext(productId, newQuantity, extractCorrelation(ctx))
                .onItem()
                .invoke(updatedProduct -> {
                    JsonObject response = new JsonObject()
                            .put(JsonKeys.MESSAGE, ProductConstants.MESSAGE_PRODUCT_STOCK_UPDATED)
                            .put(ProductConstants.KEY_PRODUCT, updatedProduct);
                    RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, response);
                })
                .replaceWithVoid();
    }

    /**
     * Sends an analytics report request over the EventBus and returns the result.
     *
     * <p>Uses the correlation context seeded by RouterHelper.handleAsync — no duplicate wrapper
     * creation. EventBus reply timeout is bounded by {@code app.analytics.execution-timeout-ms}
     * via DeliveryOptions so the request does not hang for the default 30 s.
     */
    private Uni<Void> generateAnalyticsReport(RoutingContext ctx) {
        ContextAwareVertxWrapper wrapper = ctx.get(ContextKeys.ROUTING_CONTEXT_WRAPPER);

        JsonObject requestData = new JsonObject()
                .put(ProductConstants.KEY_REPORT_TYPE, ProductConstants.VALUE_REPORT_TYPE_ANALYTICS)
                .put(JsonKeys.TIMESTAMP, System.currentTimeMillis());

        if (wrapper != null) {
            wrapper.enrichEventBusMessage(requestData);
            wrapper.logEvent("analytics_request_received", "endpoint", ProductConstants.ROUTE_ANALYTICS_REPORT);
        }

        String analyticsAddress = appConfig.analytics().eventAddress();
        long startedAt = System.currentTimeMillis();

        DeliveryOptions opts =
                new DeliveryOptions().setSendTimeout(appConfig.analytics().executionTimeoutMs());

        return Uni.createFrom()
                .<JsonObject>emitter(
                        em -> ctx.vertx().eventBus().request(analyticsAddress, requestData, opts, reply -> {
                            long elapsed = System.currentTimeMillis() - startedAt;
                            if (reply.succeeded()) {
                                metricsFacade.recordEventBusRequest(
                                        analyticsAddress, OutcomeConstants.SUCCESS, elapsed);
                                em.complete(new JsonObject(reply.result().body().toString()));
                            } else {
                                metricsFacade.recordEventBusRequest(analyticsAddress, OutcomeConstants.ERROR, elapsed);
                                em.fail(mapReplyFailure(reply.cause(), ProductConstants.MESSAGE_ANALYTICS_FAILED));
                            }
                        }))
                .onItem()
                .invoke(report -> RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, report))
                .replaceWithVoid();
    }

    /**
     * Dispatches a batch operation request over the EventBus.
     *
     * <p>Same pattern as {@link #generateAnalyticsReport}: uses existing wrapper, bounded timeout.
     */
    private Uni<Void> processBatchOperation(RoutingContext ctx) {
        ContextAwareVertxWrapper wrapper = ctx.get(ContextKeys.ROUTING_CONTEXT_WRAPPER);
        String operation = ctx.pathParam(ProductConstants.PARAM_OPERATION);

        JsonObject requestData =
                new JsonObject().put(JsonKeys.OPERATION, operation).put(JsonKeys.TIMESTAMP, System.currentTimeMillis());

        if (ProductConstants.VALUE_OPERATION_DELETE.equalsIgnoreCase(operation)) {
            String confirm = ctx.request().getParam(ProductConstants.QUERY_CONFIRM);
            requestData.put(ProductConstants.KEY_CONFIRM_DELETE, ProductConstants.VALUE_CONFIRM_TRUE.equals(confirm));
        }

        if (wrapper != null) {
            wrapper.enrichEventBusMessage(requestData);
            wrapper.logEvent("batch_operation_request_received", JsonKeys.OPERATION, operation);
        }

        String batchAddress = appConfig.eventBus().batchOperationAddress();
        long startedAt = System.currentTimeMillis();

        DeliveryOptions opts =
                new DeliveryOptions().setSendTimeout(appConfig.eventBus().requestTimeoutMs());

        return Uni.createFrom()
                .<JsonObject>emitter(em -> ctx.vertx().eventBus().request(batchAddress, requestData, opts, reply -> {
                    long elapsed = System.currentTimeMillis() - startedAt;
                    if (reply.succeeded()) {
                        metricsFacade.recordEventBusRequest(batchAddress, OutcomeConstants.SUCCESS, elapsed);
                        em.complete(new JsonObject(reply.result().body().toString()));
                    } else {
                        metricsFacade.recordEventBusRequest(batchAddress, OutcomeConstants.ERROR, elapsed);
                        em.fail(mapReplyFailure(reply.cause(), ProductConstants.MESSAGE_BATCH_FAILED));
                    }
                }))
                .onItem()
                .invoke(result -> RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, result))
                .replaceWithVoid();
    }

    /** Maps an EventBus reply failure to a ServiceException, preserving the failure code. */
    private static ServiceException mapReplyFailure(Throwable cause, String defaultMessage) {
        if (cause instanceof ReplyException re) {
            int code = re.failureCode() > 0 ? re.failureCode() : HttpStatusCodes.INTERNAL_SERVER_ERROR;
            String msg = re.getMessage() != null ? re.getMessage() : defaultMessage;
            return new ServiceException(msg, code);
        }
        return new ServiceException(defaultMessage, HttpStatusCodes.INTERNAL_SERVER_ERROR);
    }

    /** Extracts the CorrelationContext seeded by RouterHelper.handleAsync. */
    private static CorrelationContext extractCorrelation(RoutingContext ctx) {
        ContextAwareVertxWrapper wrapper = ctx.get(ContextKeys.ROUTING_CONTEXT_WRAPPER);
        return wrapper != null ? wrapper.getCorrelationContext() : null;
    }
}
