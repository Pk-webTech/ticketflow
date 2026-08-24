package com.ticketflow.auth.exception;

import com.ticketflow.auth.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        // 4xx application errors are expected traffic, not failures — INFO is
        // enough to see them without treating every 403/404 as noise.
        log.info("{} on {} -> {} {}", ex.getClass().getSimpleName(), request.getRequestURI(),
                ex.getStatus().value(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                ex.getStatus().value(), ex.getStatus().getReasonPhrase(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "Invalid email or password", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "Validation Failed", "One or more fields are invalid",
                request.getRequestURI(), details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Catch-all for genuinely unexpected failures.
     *
     * THIS WAS THE BUG: this handler previously swallowed every exception
     * without logging it, so a real 500 (NPE, misconfigured bean, bad SQL,
     * whatever) was completely invisible in the logs — the container looked
     * idle even while actively failing every request. Always log the full
     * exception here; a generic message to the CLIENT is fine (don't leak
     * internals), but the SERVER log must have the real stack trace or every
     * unexpected failure becomes undebuggable.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                "An unexpected error occurred", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}