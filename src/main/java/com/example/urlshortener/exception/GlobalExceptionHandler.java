package com.example.urlshortener.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("error", "Validation failed");

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(error.getField(), error.getDefaultMessage())
                );
        response.put("errors", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("error", "Conflict");
        response.put("message", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNoSuchElementException(
            NoSuchElementException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("error", "Not Found");
        response.put("message", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    /**
     * Handles database unique-constraint violations that slip past the application-level
     * duplicate check — for example when two concurrent requests create the same custom
     * alias simultaneously.
     *
     * Only the short_code uniqueness constraint is translated to HTTP 409. The check uses
     * the exact PostgreSQL-generated constraint name {@code url_mappings_short_code_key}
     * so that unrelated integrity violations (null constraints, FK violations, etc.) still
     * produce HTTP 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {

        // Walk the cause chain to find the underlying SQL exception message.
        // PostgreSQL names the inline UNIQUE constraint on url_mappings.short_code
        // as "url_mappings_short_code_key" — match that exact string only.
        Throwable cause = ex;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && msg.toLowerCase().contains("url_mappings_short_code_key")) {
                Map<String, Object> response = new HashMap<>();
                response.put("error", "Conflict");
                response.put("message", "A URL with that short code already exists.");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
            cause = cause.getCause();
        }

        // Not a short_code conflict — return 500 so the caller sees the real failure.
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Internal Server Error");
        response.put("message", "A database integrity constraint was violated.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
