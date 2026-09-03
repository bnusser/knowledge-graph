package com.knowledgegraph.core.exception;

/** Raised when a syntactically valid traversal request violates a semantic bound or filter rule. */
public class InvalidTraversalRequestException extends RuntimeException {

    public InvalidTraversalRequestException(String message) {
        super(message);
    }
}
