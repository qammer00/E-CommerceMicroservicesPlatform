package com.ecommerce.orderservice.exception;
public class InactiveProductException extends RuntimeException {
    public InactiveProductException(String productId) { super("Product is inactive: " + productId); }
}
