package com.ecommerce.userservice.dto;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp,
        String requestId
) {
    public static <T> ApiResponse<T> success(String message, T data, String requestId) {
        return new ApiResponse<>(true, message, data, Instant.now(), requestId);
    }

    public static <T> ApiResponse<T> error(String message, T data, String requestId) {
        return new ApiResponse<>(false, message, data, Instant.now(), requestId);
    }
}
