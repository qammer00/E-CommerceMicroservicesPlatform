package com.ecommerce.paymentservice.exception;
public class ForbiddenAccessException extends RuntimeException {
    public ForbiddenAccessException(String message) { super(message); }
}
