package com.ecommerce.paymentservice.exception;

import com.ecommerce.paymentservice.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", errors, requestId(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.error(
                "Invalid request body", null, requestId(request)));
    }

    @ExceptionHandler({PaymentNotFoundException.class, PaymentOrderNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage(), null, requestId(request)));
    }

    @ExceptionHandler(PaymentAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(PaymentAlreadyExistsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage(), null, requestId(request)));
    }

    @ExceptionHandler({InvalidPaymentStatusTransitionException.class, InvalidOrderForPaymentException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), null, requestId(request)));
    }

    @ExceptionHandler({ForbiddenAccessException.class, PaymentOrderMismatchException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleForbidden(RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                ex.getMessage() != null ? ex.getMessage() : "Access denied", null, requestId(request)));
    }

    @ExceptionHandler({UnauthorizedAccessException.class, AuthenticationException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(
                ex.getMessage() != null ? ex.getMessage() : "Authentication required", null, requestId(request)));
    }

    @ExceptionHandler(PaymentOrderUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnavailable(PaymentOrderUnavailableException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(ex.getMessage(), null, requestId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred", null, requestId(request)));
    }

    private String requestId(HttpServletRequest request) {
        return request.getHeader("X-Request-Id");
    }
}
