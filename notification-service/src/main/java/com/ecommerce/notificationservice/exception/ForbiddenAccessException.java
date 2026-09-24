package com.ecommerce.notificationservice.exception;
public class ForbiddenAccessException extends RuntimeException {
    public ForbiddenAccessException(String message) { super(message); }
}
