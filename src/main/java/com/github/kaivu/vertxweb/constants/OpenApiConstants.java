package com.github.kaivu.vertxweb.constants;

public final class OpenApiConstants {
    private OpenApiConstants() {}

    public static final String RESOURCE_YAML = "openapi/openapi.yaml";
    public static final String RESOURCE_JSON = "openapi/openapi.json";
    public static final String RESOURCE_FALLBACK_YAML = "META-INF/openapi.yaml";
    public static final String ROUTE_YAML = "/openapi.yaml";
    public static final String ROUTE_JSON = "/openapi.json";
    public static final String ROUTE_DOCS = "/docs";
    public static final String ROUTE_DOCS_SLASH = "/docs/";
    public static final String CONTENT_TYPE_YAML = "application/yaml; " + HttpConstants.CHARSET_UTF8;
    public static final String CONTENT_TYPE_JSON = HttpConstants.CONTENT_TYPE_JSON + "; " + HttpConstants.CHARSET_UTF8;
    public static final String CONTENT_TYPE_HTML = HttpConstants.CONTENT_TYPE_HTML + "; " + HttpConstants.CHARSET_UTF8;
}
