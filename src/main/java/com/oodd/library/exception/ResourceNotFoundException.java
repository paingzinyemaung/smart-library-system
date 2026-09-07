package com.oodd.library.exception;

/**
 * Thrown when a requested entity (book, member, category, resource, loan)
 * does not exist. Mapped to HTTP 404 by the {@link GlobalExceptionHandler}
 * for both REST callers and browser pages.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found with id: " + id);
    }
}
