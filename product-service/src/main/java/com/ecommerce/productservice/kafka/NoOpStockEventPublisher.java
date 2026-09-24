package com.ecommerce.productservice.kafka;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false")
public class NoOpStockEventPublisher implements StockEventPublisher {

    @Override
    public void publish(String eventType, String productId, Map<String, Object> payload) {
        // disabled in tests / local without Kafka
    }
}
