package com.ecommerce.userservice.dto;

import com.ecommerce.userservice.entity.UserRole;
import java.time.Instant;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        UserRole role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
