package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.event.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class DomainEventListeners {

    private static final Logger log = LoggerFactory.getLogger(DomainEventListeners.class);

    private final DomainEventHandler domainEventHandler;
    private final ObjectMapper objectMapper;

    public DomainEventListeners(DomainEventHandler domainEventHandler, ObjectMapper objectMapper) {
        this.domainEventHandler = domainEventHandler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_EVENTS, groupId = "notification-service-group")
    public void onOrderEvent(@Payload String payload) {
        handle(payload);
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS, groupId = "notification-service-group")
    public void onPaymentEvent(@Payload String payload) {
        handle(payload);
    }

    private void handle(String payload) {
        try {
            DomainEvent event = objectMapper.readValue(payload, DomainEvent.class);
            log.info("Consumed eventType={} eventId={} aggregateId={}",
                    event.eventType(), event.eventId(), event.aggregateId());
            domainEventHandler.handle(event);
        } catch (Exception ex) {
            log.error("Failed to process Kafka payload: {}", ex.getMessage());
            throw new IllegalStateException("Kafka event processing failed", ex);
        }
    }
}
