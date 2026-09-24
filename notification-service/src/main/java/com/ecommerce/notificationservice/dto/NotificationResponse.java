package com.ecommerce.notificationservice.dto;

import com.ecommerce.notificationservice.entity.NotificationType;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long userId,
        NotificationType type,
        String title,
        String message,
        String referenceId,
        String referenceType,
        boolean read,
        Instant createdAt
) {
}
