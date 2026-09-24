package com.ecommerce.notificationservice.dto;

import com.ecommerce.notificationservice.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateNotificationRequest(
        @NotNull(message = "User ID is required")
        @Positive(message = "User ID must be positive")
        Long userId,

        @NotNull(message = "Type is required")
        NotificationType type,

        @NotBlank(message = "Title is required")
        @Size(max = 200)
        String title,

        @NotBlank(message = "Message is required")
        @Size(max = 1000)
        String message,

        @Size(max = 64)
        String referenceId,

        @Size(max = 64)
        String referenceType
) {
}
