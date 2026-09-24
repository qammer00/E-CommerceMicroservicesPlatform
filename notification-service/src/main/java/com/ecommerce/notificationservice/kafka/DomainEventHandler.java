package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.entity.NotificationType;
import com.ecommerce.notificationservice.entity.ProcessedEvent;
import com.ecommerce.notificationservice.event.DomainEvent;
import com.ecommerce.notificationservice.repository.ProcessedEventRepository;
import com.ecommerce.notificationservice.service.NotificationCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DomainEventHandler {

    private static final Logger log = LoggerFactory.getLogger(DomainEventHandler.class);

    private final NotificationCreator notificationCreator;
    private final ProcessedEventRepository processedEventRepository;

    public DomainEventHandler(
            NotificationCreator notificationCreator,
            ProcessedEventRepository processedEventRepository) {
        this.notificationCreator = notificationCreator;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public void handle(DomainEvent event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException("Domain event requires eventId");
        }
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Skipping duplicate eventId={} eventType={}", event.eventId(), event.eventType());
            return;
        }

        NotificationType type = mapType(event.eventType());
        if (type == null) {
            log.warn("Ignoring unsupported eventType={} eventId={}", event.eventType(), event.eventId());
            markProcessed(event);
            return;
        }

        Long userId = parseUserId(event.userId());
        if (userId == null) {
            throw new IllegalArgumentException("Domain event requires userId for notifications: " + event.eventId());
        }

        String title = titleFor(type);
        String message = messageFor(type, event);
        notificationCreator.create(
                userId,
                type,
                title,
                message,
                event.aggregateId(),
                event.aggregateType());

        try {
            markProcessed(event);
        } catch (DataIntegrityViolationException duplicate) {
            log.info("Concurrent duplicate eventId={} ignored", event.eventId());
        }
        log.info("Created notification for eventType={} eventId={} userId={}",
                event.eventType(), event.eventId(), userId);
    }

    private void markProcessed(DomainEvent event) {
        processedEventRepository.save(new ProcessedEvent(event.eventId(), event.eventType()));
    }

    private static Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static NotificationType mapType(String eventType) {
        if (eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "ORDER_CREATED" -> NotificationType.ORDER_CREATED;
            case "ORDER_CONFIRMED" -> NotificationType.ORDER_CONFIRMED;
            case "ORDER_PROCESSING" -> NotificationType.ORDER_PROCESSING;
            case "ORDER_SHIPPED" -> NotificationType.ORDER_SHIPPED;
            case "ORDER_DELIVERED" -> NotificationType.ORDER_DELIVERED;
            case "ORDER_CANCELLED" -> NotificationType.ORDER_CANCELLED;
            case "PAYMENT_CREATED" -> NotificationType.PAYMENT_PENDING;
            case "PAYMENT_PAID" -> NotificationType.PAYMENT_PAID;
            case "PAYMENT_FAILED" -> NotificationType.PAYMENT_FAILED;
            case "PAYMENT_REFUNDED" -> NotificationType.PAYMENT_REFUNDED;
            default -> null;
        };
    }

    private static String titleFor(NotificationType type) {
        return switch (type) {
            case ORDER_CREATED -> "Order created";
            case ORDER_CONFIRMED -> "Order confirmed";
            case ORDER_PROCESSING -> "Order processing";
            case ORDER_SHIPPED -> "Order shipped";
            case ORDER_DELIVERED -> "Order delivered";
            case ORDER_CANCELLED -> "Order cancelled";
            case PAYMENT_PENDING -> "Payment created";
            case PAYMENT_PAID -> "Payment successful";
            case PAYMENT_FAILED -> "Payment failed";
            case PAYMENT_REFUNDED -> "Payment refunded";
        };
    }

    private static String messageFor(NotificationType type, DomainEvent event) {
        return titleFor(type) + " for " + event.aggregateType() + " " + event.aggregateId();
    }
}
