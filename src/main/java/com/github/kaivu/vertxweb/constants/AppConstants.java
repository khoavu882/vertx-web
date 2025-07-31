package com.github.kaivu.vertxweb.constants;

public final class AppConstants {
    private AppConstants() {
        // Prevent instantiation
    }

    public static final class Http {
        public static final String CONTENT_TYPE_JSON = "application/json";
        public static final String CONTENT_TYPE_TEXT = "text/plain";
        public static final String CONTENT_TYPE_HTML = "text/html";
        public static final String CHARSET_UTF8 = "charset=utf-8";
        public static final String AUTHORIZATION = "Authorization";
    }

    public static final class Status {
        public static final int OK = 200;
        public static final int CREATED = 201;
        public static final int BAD_REQUEST = 400;
        public static final int UNAUTHORIZED = 401;
        public static final int NOT_FOUND = 404;
        public static final int INTERNAL_SERVER_ERROR = 500;
    }

    public static final class Auth {
        public static final String AUTH_SCHEME = "Bearer ";
    }

    public static final class Messages {
        public static final String INVALID_TOKEN = "Invalid Token";
        public static final String MISSING_BODY = "Request body is required";
        public static final String INVALID_USER_DATA = "Invalid user data. Required fields: name, email";
        public static final String MISSING_PATH_PARAM = "Missing required path parameter: ";
    }
}
