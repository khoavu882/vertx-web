package com.github.kaivu.vertxweb.repositories.exception;

/**
 * Thrown when the repository cannot reach or communicate with the data store.
 *
 * <p>Maps to HTTP 503 Service Unavailable. The circuit breaker counts this exception
 * class as a failure (status &ge; 500) and will trip after the configured threshold.
 */
public class RepositoryConnectionException extends RepositoryException {

    public RepositoryConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
