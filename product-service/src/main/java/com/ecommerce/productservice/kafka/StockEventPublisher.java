package com.ecommerce.productservice.kafka;

import java.util.Map;

public interface StockEventPublisher {
    void publish(String eventType, String productId, Map<String, Object> payload);
}
