package com.ecommerce.orderservice.event;

public final class OrderEventTypes {
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_PROCESSING = "ORDER_PROCESSING";
    public static final String ORDER_SHIPPED = "ORDER_SHIPPED";
    public static final String ORDER_DELIVERED = "ORDER_DELIVERED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    private OrderEventTypes() {
    }
}
