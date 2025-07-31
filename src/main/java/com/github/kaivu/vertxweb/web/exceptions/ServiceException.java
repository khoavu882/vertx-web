package com.github.kaivu.vertxweb.web.exceptions;

public class ServiceException extends RuntimeException {
    private final Integer statusCode;

    public ServiceException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
