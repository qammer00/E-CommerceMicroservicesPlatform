package com.ecommerce.paymentservice.event;

import java.time.Instant;
import java.util.Map;

public record DomainEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String aggregateId,
        String aggregateType,
        String userId,
        Map<String, Object> payload
) {
}
