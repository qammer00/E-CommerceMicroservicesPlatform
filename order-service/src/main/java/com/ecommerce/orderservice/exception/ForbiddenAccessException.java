package com.ecommerce.orderservice.exception;
public class ForbiddenAccessException extends RuntimeException {
    public ForbiddenAccessException(String message) { super(message); }
}
