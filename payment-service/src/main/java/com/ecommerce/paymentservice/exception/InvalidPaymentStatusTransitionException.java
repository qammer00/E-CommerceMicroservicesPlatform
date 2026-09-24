package com.ecommerce.paymentservice.exception;
import com.ecommerce.paymentservice.entity.PaymentStatus;
public class InvalidPaymentStatusTransitionException extends RuntimeException {
    public InvalidPaymentStatusTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Invalid payment status transition from " + from + " to " + to);
    }
}
