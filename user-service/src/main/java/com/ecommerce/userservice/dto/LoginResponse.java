package com.ecommerce.userservice.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInMs,
        UserResponse user
) {
}
