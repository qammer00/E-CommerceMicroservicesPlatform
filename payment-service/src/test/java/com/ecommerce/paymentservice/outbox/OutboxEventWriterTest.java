package com.ecommerce.paymentservice.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.paymentservice.event.DomainEvent;
import com.ecommerce.paymentservice.event.PaymentEventTypes;
import com.ecommerce.paymentservice.kafka.KafkaTopics;
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
    void enqueuePaymentEvent_shouldPersistPendingOutboxRow() {
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        DomainEvent event = outboxEventWriter.enqueuePaymentEvent(
                PaymentEventTypes.PAYMENT_PAID,
                "9",
                "45",
                Map.of("status", "PAID"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(event.eventType()).isEqualTo(PaymentEventTypes.PAYMENT_PAID);
        assertThat(saved.getTopic()).isEqualTo(KafkaTopics.PAYMENT_EVENTS);
        assertThat(saved.getMessageKey()).isEqualTo("9");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getPayload()).contains("PAYMENT_PAID");
    }
}
