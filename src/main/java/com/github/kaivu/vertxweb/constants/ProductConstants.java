package com.github.kaivu.vertxweb.constants;

public final class ProductConstants {
    private ProductConstants() {}

    public static final String ROUTE_BY_ID = "/:productId";
    public static final String ROUTE_STOCK_BY_ID = "/:productId/stock";
    public static final String ROUTE_ANALYTICS_REPORT = "/analytics/report";
    public static final String ROUTE_BATCH_OPERATION = "/batch/:operation";
    public static final String PARAM_PRODUCT_ID = "productId";
    public static final String PARAM_OPERATION = "operation";
    public static final String QUERY_CONFIRM = "confirm";
    public static final String KEY_PRODUCT = "product";
    public static final String KEY_QUANTITY = "quantity";
    public static final String KEY_REPORT_TYPE = "reportType";
    public static final String KEY_CONFIRM_DELETE = "confirmDelete";
    public static final String KEY_PROCESSED_AT = "processedAt";
    public static final String VALUE_REPORT_TYPE_ANALYTICS = "analytics";
    public static final String VALUE_OPERATION_DELETE = "delete";
    public static final String MESSAGE_PRODUCT_CREATED = "Product created successfully";
    public static final String MESSAGE_PRODUCT_STOCK_UPDATED = "Product stock updated successfully";
    public static final String MESSAGE_ANALYTICS_FAILED = "Failed to generate analytics report";
    public static final String MESSAGE_BATCH_FAILED = "Batch operation failed";
    public static final String LOG_OPERATION_ANALYTICS_REPORT = "analytics-report";
    public static final String VALUE_CONFIRM_TRUE = "true";
}
