package com.ecommerce.notificationservice.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.notificationservice.entity.Notification;
import com.ecommerce.notificationservice.entity.NotificationType;
import com.ecommerce.notificationservice.entity.ProcessedEvent;
import com.ecommerce.notificationservice.event.DomainEvent;
import com.ecommerce.notificationservice.repository.ProcessedEventRepository;
import com.ecommerce.notificationservice.service.NotificationCreator;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DomainEventHandlerTest {

    @Mock
    private NotificationCreator notificationCreator;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private DomainEventHandler domainEventHandler;

    @Test
    void handle_shouldCreateNotificationForOrderCreated() {
        DomainEvent event = event("e1", "ORDER_CREATED", "10", "45");
        when(processedEventRepository.existsById("e1")).thenReturn(false);
        when(notificationCreator.create(anyLong(), any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Notification());

        domainEventHandler.handle(event);

        verify(notificationCreator).create(
                eq(45L),
                eq(NotificationType.ORDER_CREATED),
                eq("Order created"),
                anyString(),
                eq("10"),
                eq("ORDER"));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void handle_shouldMapPaymentCreatedToPaymentPending() {
        DomainEvent event = event("e2", "PAYMENT_CREATED", "7", "45");
        when(processedEventRepository.existsById("e2")).thenReturn(false);
        when(notificationCreator.create(anyLong(), any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Notification());

        domainEventHandler.handle(event);

        verify(notificationCreator).create(
                eq(45L),
                eq(NotificationType.PAYMENT_PENDING),
                eq("Payment created"),
                anyString(),
                eq("7"),
                eq("PAYMENT"));
    }

    @Test
    void handle_shouldBeIdempotentForDuplicateEventId() {
        when(processedEventRepository.existsById("dup")).thenReturn(true);

        domainEventHandler.handle(event("dup", "ORDER_CONFIRMED", "1", "2"));

        verify(notificationCreator, never()).create(anyLong(), any(), anyString(), anyString(), anyString(), anyString());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void handle_shouldCreateNotificationForPaymentPaidAndFailed() {
        when(processedEventRepository.existsById(anyString())).thenReturn(false);
        when(notificationCreator.create(anyLong(), any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Notification());

        domainEventHandler.handle(event("p1", "PAYMENT_PAID", "3", "9"));
        domainEventHandler.handle(event("p2", "PAYMENT_FAILED", "4", "9"));

        verify(notificationCreator, times(1)).create(
                eq(9L), eq(NotificationType.PAYMENT_PAID), anyString(), anyString(), eq("3"), eq("PAYMENT"));
        verify(notificationCreator, times(1)).create(
                eq(9L), eq(NotificationType.PAYMENT_FAILED), anyString(), anyString(), eq("4"), eq("PAYMENT"));
    }

    @Test
    void handle_shouldRejectMissingEventId() {
        assertThatThrownBy(() -> domainEventHandler.handle(
                new DomainEvent(null, "ORDER_CREATED", Instant.now(), "1", "ORDER", "1", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DomainEvent event(String id, String type, String aggregateId, String userId) {
        return new DomainEvent(id, type, Instant.now(), aggregateId, type.startsWith("PAYMENT") ? "PAYMENT" : "ORDER",
                userId, Map.of());
    }
}
