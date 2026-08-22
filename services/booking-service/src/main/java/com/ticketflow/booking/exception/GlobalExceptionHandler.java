package com.ticketflow.booking.exception;

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
            BookingExceptions.SeatAlreadyBookedException.class,
            BookingExceptions.HoldExpiredException.class,
            BookingExceptions.BookingAlreadyCancelledException.class,
            BookingExceptions.CancellationWindowClosedException.class
    })
    public ResponseEntity<Map<String, Object>> handleConflict(RuntimeException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({
            BookingExceptions.NotHoldOwnerException.class,
            BookingExceptions.NotBookingOwnerException.class
    })
    public ResponseEntity<Map<String, Object>> handleForbidden(RuntimeException ex) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(BookingExceptions.BookingNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BookingExceptions.UpstreamServiceException.class)
    public ResponseEntity<Map<String, Object>> handleUpstream(RuntimeException ex) {
        log.error("Upstream failure", ex);
        return body(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception in booking-service", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }
}
