package com.ecommerce.orderservice.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.orderservice.event.DomainEvent;
import com.ecommerce.orderservice.event.OrderEventTypes;
import com.ecommerce.orderservice.kafka.KafkaTopics;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventWriterTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private OutboxEventWriter outboxEventWriter;

    @Test
    void enqueueOrderEvent_shouldPersistPendingOutboxRow() {
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        DomainEvent event = outboxEventWriter.enqueueOrderEvent(
                OrderEventTypes.ORDER_CREATED,
                "123",
                "45",
                Map.of("status", "PENDING"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(event.eventType()).isEqualTo(OrderEventTypes.ORDER_CREATED);
        assertThat(saved.getTopic()).isEqualTo(KafkaTopics.ORDER_EVENTS);
        assertThat(saved.getMessageKey()).isEqualTo("123");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getPayload()).contains("ORDER_CREATED");
        assertThat(saved.getEventId()).isEqualTo(event.eventId());
    }
}
