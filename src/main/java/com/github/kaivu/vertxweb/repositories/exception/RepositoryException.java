package com.github.kaivu.vertxweb.repositories.exception;

/**
 * Base exception for all repository-layer failures.
 *
 * <p>The service layer maps subclasses to {@code ServiceException} with the appropriate HTTP
 * status code. This keeps HTTP semantics out of the repository layer entirely.
 */
public class RepositoryException extends RuntimeException {

    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
