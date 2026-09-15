package com.ecommerce.orderservice.client;

public record ProductApiResponse<T>(
        boolean success,
        String message,
        T data
) {
}
