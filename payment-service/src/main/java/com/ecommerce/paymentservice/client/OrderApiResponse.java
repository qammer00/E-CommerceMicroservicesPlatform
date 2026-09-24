package com.ecommerce.paymentservice.client;

public record OrderApiResponse<T>(
        boolean success,
        String message,
        T data
) {
}
