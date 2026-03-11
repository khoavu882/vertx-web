package com.github.kaivu.vertxweb.repositories.exception;

/** Thrown when a requested entity does not exist in the data store. Maps to HTTP 404. */
public class RepositoryNotFoundException extends RepositoryException {

    public RepositoryNotFoundException(String message) {
        super(message);
    }
}
