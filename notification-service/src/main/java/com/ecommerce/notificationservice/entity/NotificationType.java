package com.ecommerce.notificationservice.entity;

public enum NotificationType {
    ORDER_CREATED,
    ORDER_CONFIRMED,
    ORDER_PROCESSING,
    ORDER_SHIPPED,
    ORDER_DELIVERED,
    ORDER_CANCELLED,
    PAYMENT_PENDING,
    PAYMENT_PAID,
    PAYMENT_FAILED,
    PAYMENT_REFUNDED
}
