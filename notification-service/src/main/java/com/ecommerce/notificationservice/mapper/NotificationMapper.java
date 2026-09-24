package com.ecommerce.notificationservice.mapper;

import com.ecommerce.notificationservice.dto.NotificationResponse;
import com.ecommerce.notificationservice.entity.Notification;

public final class NotificationMapper {
    private NotificationMapper() {}

    public static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getUserId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getReferenceId(), n.getReferenceType(), n.isRead(), n.getCreatedAt());
    }
}
