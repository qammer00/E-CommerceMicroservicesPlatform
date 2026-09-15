package com.ecommerce.orderservice.exception;
import com.ecommerce.orderservice.entity.OrderStatus;
public class InvalidOrderStatusTransitionException extends RuntimeException {
    public InvalidOrderStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("Invalid order status transition from " + from + " to " + to);
    }
}
