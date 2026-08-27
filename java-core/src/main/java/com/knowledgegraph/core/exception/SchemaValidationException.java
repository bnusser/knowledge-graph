package com.knowledgegraph.core.exception;

/** Thrown when a request violates the active schema. Mapped to HTTP 422, always names the violated rule. */
public class SchemaValidationException extends RuntimeException {
    public SchemaValidationException(String message) {
        super(message);
    }
}
