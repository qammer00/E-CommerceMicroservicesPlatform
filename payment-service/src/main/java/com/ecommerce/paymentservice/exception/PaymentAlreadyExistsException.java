package com.ecommerce.paymentservice.exception;
public class PaymentAlreadyExistsException extends RuntimeException {
    public PaymentAlreadyExistsException(Long orderId) {
        super("An active payment already exists for order " + orderId);
    }
}
