package com.ecommerce.productservice.kafka;

import com.ecommerce.productservice.event.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Best-effort stock event publisher. Product catalog uses MongoDB without a transactional
 * outbox in this portfolio version; business stock mutations succeed even if Kafka is down.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaStockEventPublisher implements StockEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaStockEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaStockEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String eventType, String productId, Map<String, Object> payload) {
        DomainEvent event = new DomainEvent(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                productId,
                "PRODUCT",
                null,
                payload == null ? Map.of() : payload);
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaTopics.PRODUCT_EVENTS, productId, json);
            log.info("Published product eventType={} eventId={} productId={}",
                    eventType, event.eventId(), productId);
        } catch (Exception ex) {
            log.error("Failed to publish product eventType={} productId={}: {}",
                    eventType, productId, ex.getMessage());
        }
    }
}
