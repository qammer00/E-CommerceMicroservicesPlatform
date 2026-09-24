package com.ecommerce.paymentservice.exception;
public class PaymentOrderNotFoundException extends RuntimeException {
    public PaymentOrderNotFoundException(Long orderId) { super("Order not found with id: " + orderId); }
}
