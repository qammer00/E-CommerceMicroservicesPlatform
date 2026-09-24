package com.ecommerce.paymentservice.event;

public final class PaymentEventTypes {
    public static final String PAYMENT_CREATED = "PAYMENT_CREATED";
    public static final String PAYMENT_PAID = "PAYMENT_PAID";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_REFUNDED = "PAYMENT_REFUNDED";

    private PaymentEventTypes() {
    }
}
