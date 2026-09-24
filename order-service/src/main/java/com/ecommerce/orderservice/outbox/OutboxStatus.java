package com.ecommerce.orderservice.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
