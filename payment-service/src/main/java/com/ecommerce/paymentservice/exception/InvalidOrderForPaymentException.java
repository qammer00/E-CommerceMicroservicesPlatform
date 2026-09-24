package com.ecommerce.paymentservice.exception;
public class InvalidOrderForPaymentException extends RuntimeException {
    public InvalidOrderForPaymentException(String message) { super(message); }
}
