package com.ecommerce.notificationservice.service;

import com.ecommerce.notificationservice.dto.CreateNotificationRequest;
import com.ecommerce.notificationservice.dto.NotificationResponse;
import com.ecommerce.notificationservice.dto.UnreadCountResponse;
import com.ecommerce.notificationservice.entity.Notification;
import com.ecommerce.notificationservice.exception.ForbiddenAccessException;
import com.ecommerce.notificationservice.exception.NotificationNotFoundException;
import com.ecommerce.notificationservice.mapper.NotificationMapper;
import com.ecommerce.notificationservice.repository.NotificationRepository;
import com.ecommerce.notificationservice.security.AuthenticatedUser;
import com.ecommerce.notificationservice.security.SecurityUtils;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationCreator notificationCreator;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationCreator notificationCreator) {
        this.notificationRepository = notificationRepository;
        this.notificationCreator = notificationCreator;
    }

    @Transactional
    public NotificationResponse createForAdmin(CreateNotificationRequest request) {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        if (!SecurityUtils.isAdmin(currentUser)) {
            throw new ForbiddenAccessException("Only administrators can create notifications");
        }
        return NotificationMapper.toResponse(notificationCreator.create(request));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMyNotifications() {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(NotificationMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        return new UnreadCountResponse(
                notificationRepository.countByUserIdAndReadFalse(currentUser.getUserId()));
    }

    @Transactional(readOnly = true)
    public NotificationResponse getById(Long id) {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        if (!SecurityUtils.isAdmin(currentUser) && !notification.getUserId().equals(currentUser.getUserId())) {
            throw new ForbiddenAccessException("You are not allowed to view this notification");
        }
        return NotificationMapper.toResponse(notification);
    }

    @Transactional
    public NotificationResponse markRead(Long id) {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        Notification notification = notificationRepository.findByIdAndUserId(id, currentUser.getUserId())
                .orElseThrow(() -> new NotificationNotFoundException(id));
        notification.setRead(true);
        return NotificationMapper.toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public UnreadCountResponse markAllRead() {
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        notificationRepository.markAllReadForUser(currentUser.getUserId());
        return new UnreadCountResponse(0);
    }
}
