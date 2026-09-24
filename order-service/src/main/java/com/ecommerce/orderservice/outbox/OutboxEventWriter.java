package com.ecommerce.orderservice.outbox;

import com.ecommerce.orderservice.event.DomainEvent;
import com.ecommerce.orderservice.kafka.KafkaTopics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxEventWriter {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventWriter.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventWriter(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DomainEvent enqueueOrderEvent(
            String eventType,
            String orderId,
            String userId,
            Map<String, Object> payload) {
        DomainEvent event = new DomainEvent(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                orderId,
                "ORDER",
                userId,
                payload == null ? Map.of() : payload);

        OutboxEvent outbox = new OutboxEvent();
        outbox.setEventId(event.eventId());
        outbox.setTopic(KafkaTopics.ORDER_EVENTS);
        outbox.setMessageKey(orderId);
        outbox.setEventType(eventType);
        try {
            outbox.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize domain event", e);
        }
        outbox.setStatus(OutboxStatus.PENDING);
        outboxEventRepository.save(outbox);
        log.info("Outbox enqueued eventType={} eventId={} aggregateId={}",
                eventType, event.eventId(), orderId);
        return event;
    }
}
