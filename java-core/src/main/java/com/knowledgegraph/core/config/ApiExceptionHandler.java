package com.knowledgegraph.core.config;

import com.knowledgegraph.core.exception.ConflictException;
import com.knowledgegraph.core.exception.InvalidTraversalRequestException;
import com.knowledgegraph.core.exception.NotFoundException;
import com.knowledgegraph.core.exception.SchemaValidationException;
import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain exceptions to the HTTP status codes documented in contracts/openapi.yaml. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Safety net for the rare race where two concurrent creates both pass the service-layer duplicate pre-check (FR-014). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(DataIntegrityViolationException e) {
        return body(HttpStatus.CONFLICT, "Duplicate entity: violates a schema uniqueness constraint");
    }

    @ExceptionHandler(SchemaValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(SchemaValidationException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(InvalidTraversalRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTraversal(InvalidTraversalRequestException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMalformedRequest(MethodArgumentTypeMismatchException e) {
        return body(HttpStatus.BAD_REQUEST, "Unable to parse query parameter '" + e.getName() + "'");
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("timestamp", Instant.now().toString(), "status", status.value(), "error", message));
    }
}
