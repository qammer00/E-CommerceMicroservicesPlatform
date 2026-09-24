package com.ecommerce.paymentservice.exception;
public class PaymentOrderUnavailableException extends RuntimeException {
    public PaymentOrderUnavailableException(String message) { super(message); }
    public PaymentOrderUnavailableException(String message, Throwable cause) { super(message, cause); }
}
