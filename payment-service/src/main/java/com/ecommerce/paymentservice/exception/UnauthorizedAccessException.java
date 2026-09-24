package com.ecommerce.paymentservice.exception;
public class UnauthorizedAccessException extends RuntimeException {
    public UnauthorizedAccessException(String message) { super(message); }
}
