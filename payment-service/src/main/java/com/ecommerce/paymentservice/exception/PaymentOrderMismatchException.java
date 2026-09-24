package com.ecommerce.paymentservice.exception;
public class PaymentOrderMismatchException extends RuntimeException {
    public PaymentOrderMismatchException(String message) { super(message); }
}
