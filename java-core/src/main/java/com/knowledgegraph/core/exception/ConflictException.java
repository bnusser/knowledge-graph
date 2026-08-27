package com.knowledgegraph.core.exception;

/** Thrown for a naming/uniqueness conflict (duplicate entity, duplicate schema type name). Mapped to HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
