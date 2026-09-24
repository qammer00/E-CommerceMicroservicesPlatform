package com.ecommerce.paymentservice.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    @Bean
    NewTopic paymentEventsTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentEventsDltTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_EVENTS + ".DLT").partitions(3).replicas(1).build();
    }
}
