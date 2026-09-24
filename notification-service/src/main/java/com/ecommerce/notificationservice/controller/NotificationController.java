package com.ecommerce.notificationservice.controller;

import com.ecommerce.notificationservice.dto.ApiResponse;
import com.ecommerce.notificationservice.dto.CreateNotificationRequest;
import com.ecommerce.notificationservice.dto.NotificationResponse;
import com.ecommerce.notificationservice.dto.UnreadCountResponse;
import com.ecommerce.notificationservice.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "In-app notification APIs")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @Operation(summary = "Create a notification (ADMIN only)")
    public ResponseEntity<ApiResponse<NotificationResponse>> create(
            @Valid @RequestBody CreateNotificationRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Notification created successfully",
                notificationService.createForAdmin(request),
                requestId(httpRequest)));
    }

    @GetMapping
    @Operation(summary = "List current user's notifications")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> listMine(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Notifications retrieved successfully",
                notificationService.listMyNotifications(),
                requestId(httpRequest)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Unread count retrieved successfully",
                notificationService.unreadCount(),
                requestId(httpRequest)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get notification by id")
    public ResponseEntity<ApiResponse<NotificationResponse>> getById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Notification retrieved successfully",
                notificationService.getById(id),
                requestId(httpRequest)));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Notification marked as read",
                notificationService.markRead(id),
                requestId(httpRequest)));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> markAllRead(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "All notifications marked as read",
                notificationService.markAllRead(),
                requestId(httpRequest)));
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return requestId != null && !requestId.isBlank() ? requestId : UUID.randomUUID().toString();
    }
}
