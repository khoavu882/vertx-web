package com.github.kaivu.vertxweb.web;

import com.github.kaivu.vertxweb.constants.AppConstants;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.github.kaivu.vertxweb.web.validation.ValidationResult;
import com.google.inject.Singleton;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RouterHelper provides utility methods for handling common Vert.x routing patterns
 * with Mutiny reactive programming and consistent error handling.
 *
 * <p>This helper eliminates repetitive code in router classes by providing:
 * - Standardized reactive response handling
 * - Consistent error response formatting
 * - Validation error handling
 * - Path parameter and request body validation
 * - Proper content-type management
 *
 * <p>Designed to work seamlessly with the dual-verticle architecture,
 * Google Guice dependency injection, and the middleware pipeline.
 */
@Singleton
public class RouterHelper {

    private static final Logger log = LoggerFactory.getLogger(RouterHelper.class);

    private static final String CONTENT_TYPE = AppConstants.Http.CONTENT_TYPE_JSON;
    private static final String CHARSET = AppConstants.Http.CHARSET_UTF8;
    private static final String FULL_CONTENT_TYPE = CONTENT_TYPE + "; " + CHARSET;

    /**
     * Static method for handling async routing with clean functional pattern.
     *
     * <p>Usage pattern:
     * router.get().handler(ctx -> RouterHelper.handleAsync(ctx, this::getAllUsers));
     *
     * private Uni<Void> getAllUsers(RoutingContext ctx) {
     *     return userService.getAllUsers()
     *         .onItem().invoke(users -> RouterHelper.sendJsonResponse(ctx, 200, users))
     *         .replaceWithVoid();
     * }
     *
     * @param ctx The routing context
     * @param handler The async handler function that returns Uni<Void>
     */
    public static void handleAsync(RoutingContext ctx, Function<RoutingContext, Uni<Void>> handler) {
        try {
            handler.apply(ctx)
                    .subscribe()
                    .with(
                            ignored -> {
                                // Success case - response should already be sent by handler
                                if (!ctx.response().ended()) {
                                    log.warn(
                                            "Handler completed but response was not sent for: {} {}",
                                            ctx.request().method(),
                                            ctx.request().uri());
                                }
                            },
                            failure -> handleFailure(ctx, failure));
        } catch (Exception e) {
            handleFailure(ctx, e);
        }
    }

    /**
     * Handles a Uni response with standard success and error handling.
     * This is the primary method for processing service responses.
     *
     * @param <T> The type of the Uni result
     * @param uni The Uni to handle
     * @param ctx The routing context
     * @param statusCode The HTTP status code for successful responses
     * @return A Consumer that can be used to handle the Uni result
     */
    public <T> Consumer<T> handleUniResponse(Uni<JsonObject> uni, RoutingContext ctx, int statusCode) {
        return result -> {
            uni.subscribe()
                    .with(
                            response -> sendJsonResponse(ctx, statusCode, response),
                            failure -> handleFailure(ctx, failure));
        };
    }

    /**
     * Handles a Uni response with custom success handling.
     *
     * @param <T> The type of the Uni result
     * @param uni The Uni to handle
     * @param ctx The routing context
     * @param statusCode The HTTP status code for successful responses
     * @param successTransform Function to transform the service result before sending response
     */
    public <T> void handleUniResponseWithTransform(
            Uni<JsonObject> uni,
            RoutingContext ctx,
            int statusCode,
            Function<JsonObject, JsonObject> successTransform) {

        uni.subscribe()
                .with(
                        result -> sendJsonResponse(ctx, statusCode, successTransform.apply(result)),
                        failure -> handleFailure(ctx, failure));
    }

    /**
     * Handles a service Uni directly with standard error handling.
     * This method subscribes to the Uni and handles both success and failure cases.
     *
     * @param uni The service Uni to handle
     * @param ctx The routing context
     * @param statusCode The HTTP status code for successful responses
     */
    public void handleServiceUni(Uni<JsonObject> uni, RoutingContext ctx, int statusCode) {
        uni.subscribe()
                .with(response -> sendJsonResponse(ctx, statusCode, response), failure -> handleFailure(ctx, failure));
    }

    /**
     * Handles a service Uni with custom success message wrapping.
     *
     * @param uni The service Uni to handle
     * @param ctx The routing context
     * @param statusCode The HTTP status code for successful responses
     * @param successMessage The success message to include in the response
     */
    public void handleServiceUniWithMessage(
            Uni<JsonObject> uni, RoutingContext ctx, int statusCode, String successMessage) {

        handleUniResponseWithTransform(uni, ctx, statusCode, result -> new JsonObject()
                .put("message", successMessage)
                .put("data", result));
    }

    /**
     * Handles a service Uni with custom success message and data key.
     *
     * @param uni The service Uni to handle
     * @param ctx The routing context
     * @param statusCode The HTTP status code for successful responses
     * @param successMessage The success message to include in the response
     * @param dataKey The key to use for the service result in the response
     */
    public void handleServiceUniWithCustomMessage(
            Uni<JsonObject> uni, RoutingContext ctx, int statusCode, String successMessage, String dataKey) {

        handleUniResponseWithTransform(uni, ctx, statusCode, result -> new JsonObject()
                .put("message", successMessage)
                .put(dataKey, result));
    }

    /**
     * Validates a path parameter and throws ServiceException if invalid.
     *
     * @param ctx The routing context
     * @param paramName The name of the path parameter
     * @return The validated parameter value
     * @throws ServiceException if the parameter is null or empty
     */
    public String validatePathParam(RoutingContext ctx, String paramName) {
        String value = ctx.pathParam(paramName);
        if (value == null || value.trim().isEmpty()) {
            throw new ServiceException(
                    AppConstants.Messages.MISSING_PATH_PARAM + paramName, AppConstants.Status.BAD_REQUEST);
        }
        return value;
    }

    /**
     * Validates and returns the request body as JsonObject.
     *
     * @param ctx The routing context
     * @return The request body as JsonObject
     * @throws ServiceException if the body is not available
     */
    public JsonObject validateRequestBody(RoutingContext ctx) {
        if (!ctx.body().available()) {
            throw new ServiceException(AppConstants.Messages.MISSING_BODY, AppConstants.Status.BAD_REQUEST);
        }
        return ctx.body().asJsonObject();
    }

    /**
     * Validates request body and applies validation rules.
     *
     * @param ctx The routing context
     * @param validationResult The validation result to check
     * @return The validated JsonObject body
     * @throws ServiceException if validation fails
     */
    public JsonObject validateRequestBodyWithRules(RoutingContext ctx, ValidationResult validationResult) {
        JsonObject body = validateRequestBody(ctx);

        if (!validationResult.isValid()) {
            throw new ServiceException(
                    "Validation failed: " + validationResult.getAllErrors(), AppConstants.Status.BAD_REQUEST);
        }

        return body;
    }

    /**
     * Handles validation errors by throwing a ServiceException.
     *
     * @param validationResult The validation result
     * @throws ServiceException if validation failed
     */
    public void handleValidationErrors(ValidationResult validationResult) {
        if (!validationResult.isValid()) {
            throw new ServiceException(
                    "Validation failed: " + validationResult.getAllErrors(), AppConstants.Status.BAD_REQUEST);
        }
    }

    /**
     * Sends a JSON response with the specified status code and body.
     *
     * @param ctx The routing context
     * @param statusCode The HTTP status code
     * @param response The JSON response body
     */
    public static void sendJsonResponse(RoutingContext ctx, int statusCode, JsonObject response) {
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader(HttpHeaders.CONTENT_TYPE, FULL_CONTENT_TYPE)
                .end(response.encode());
    }

    /**
     * Instance method for backwards compatibility.
     */
    public void sendJsonResponseInstance(RoutingContext ctx, int statusCode, JsonObject response) {
        sendJsonResponse(ctx, statusCode, response);
    }

    /**
     * Sends a success response with standard message format.
     *
     * @param ctx The routing context
     * @param statusCode The HTTP status code
     * @param message The success message
     * @param data The data to include in response
     */
    public void sendSuccessResponse(RoutingContext ctx, int statusCode, String message, JsonObject data) {
        JsonObject response = new JsonObject().put("message", message).put("data", data);
        sendJsonResponse(ctx, statusCode, response);
    }

    /**
     * Sends an error response with standard error format.
     *
     * @param ctx The routing context
     * @param statusCode The HTTP status code
     * @param message The error message
     */
    public void sendErrorResponse(RoutingContext ctx, int statusCode, String message) {
        JsonObject response = new JsonObject().put("error", message).put("timestamp", System.currentTimeMillis());
        sendJsonResponse(ctx, statusCode, response);
    }

    /**
     * Sends a validation error response.
     *
     * @param ctx The routing context
     * @param validationResult The validation result containing errors
     */
    public void sendValidationErrorResponse(RoutingContext ctx, ValidationResult validationResult) {
        JsonObject response = new JsonObject()
                .put("error", "Validation failed")
                .put("details", validationResult.getAllErrors())
                .put("timestamp", System.currentTimeMillis());
        sendJsonResponse(ctx, AppConstants.Status.BAD_REQUEST, response);
    }

    /**
     * Handles different types of failures and routes them appropriately.
     * This method integrates with the existing ErrorHandler middleware.
     *
     * @param ctx The routing context
     * @param throwable The failure/exception
     */
    private static void handleFailure(RoutingContext ctx, Throwable throwable) {
        log.error("Request failed: {} {}", ctx.request().method(), ctx.request().uri(), throwable);

        // This maintains consistency with the existing error handling pipeline
        ctx.fail(throwable);
    }
}
