package com.ecommerce.notificationservice.service;

import com.ecommerce.notificationservice.dto.CreateNotificationRequest;
import com.ecommerce.notificationservice.entity.Notification;
import com.ecommerce.notificationservice.entity.NotificationType;
import com.ecommerce.notificationservice.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal abstraction for creating notifications.
 * Not exposed as a public user API — callers are ADMIN ops or other services.
 */
@Service
public class NotificationCreator {

    private final NotificationRepository notificationRepository;

    public NotificationCreator(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Notification create(
            Long userId,
            NotificationType type,
            String title,
            String message,
            String referenceId,
            String referenceType) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setReferenceId(referenceId);
        notification.setReferenceType(referenceType);
        notification.setRead(false);
        return notificationRepository.save(notification);
    }

    @Transactional
    public Notification create(CreateNotificationRequest request) {
        return create(
                request.userId(),
                request.type(),
                request.title(),
                request.message(),
                request.referenceId(),
                request.referenceType());
    }
}
