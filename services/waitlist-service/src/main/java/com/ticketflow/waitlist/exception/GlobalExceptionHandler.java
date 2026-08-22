package com.ticketflow.waitlist.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("status", status.value());
        payload.put("error", status.getReasonPhrase());
        payload.put("message", message);
        return ResponseEntity.status(status).body(payload);
    }

    @ExceptionHandler({
            WaitlistExceptions.AlreadyOnWaitlistException.class,
            WaitlistExceptions.OfferExpiredException.class,
            WaitlistExceptions.OfferAlreadyResolvedException.class
    })
    public ResponseEntity<Map<String, Object>> conflict(RuntimeException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({
            WaitlistExceptions.EntryNotFoundException.class,
            WaitlistExceptions.OfferNotFoundException.class
    })
    public ResponseEntity<Map<String, Object>> notFound(RuntimeException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(WaitlistExceptions.NotEntryOwnerException.class)
    public ResponseEntity<Map<String, Object>> forbidden(RuntimeException ex) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception ex) {
        log.error("Unhandled exception in waitlist-service", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }
}
