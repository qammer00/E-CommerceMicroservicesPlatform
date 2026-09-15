package com.ecommerce.orderservice.exception;
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productId, int quantity) {
        super("Insufficient stock for product " + productId + " (requested " + quantity + ")");
    }
}
