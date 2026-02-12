package com.github.kaivu.vertxweb.constants;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
import static java.net.HttpURLConnection.HTTP_GONE;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

public final class HttpStatusCodes {
    private HttpStatusCodes() {}

    public static final int OK = HTTP_OK;
    public static final int CREATED = HTTP_CREATED;
    public static final int BAD_REQUEST = HTTP_BAD_REQUEST;
    public static final int UNAUTHORIZED = HTTP_UNAUTHORIZED;
    public static final int NOT_FOUND = HTTP_NOT_FOUND;
    public static final int GONE = HTTP_GONE;
    public static final int INTERNAL_SERVER_ERROR = HTTP_INTERNAL_ERROR;
    public static final int SERVICE_UNAVAILABLE = HTTP_UNAVAILABLE;
    public static final int GATEWAY_TIMEOUT = HTTP_GATEWAY_TIMEOUT;
}
