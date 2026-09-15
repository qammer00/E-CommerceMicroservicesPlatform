package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName,

        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        Boolean enabled
) {
}
