package com.platform.payments.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.payment-events:payment.events}")
    private String paymentEventsTopic;

    public void publish(PaymentEvent event) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventType", event.eventType());
            envelope.put("occurredAt", event.occurredAt().toString());
            envelope.put("data", event);

            String payload = objectMapper.writeValueAsString(envelope);
            // Partition by userId so all events for a user land on the same partition (ordering)
            kafkaTemplate.send(paymentEventsTopic, event.userId().toString(), payload);

            log.info("Published payment event type={} userId={} topic={}",
                    event.eventType(), event.userId(), paymentEventsTopic);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payment event type={}", event.eventType(), e);
            throw new RuntimeException("Payment event serialization failed", e);
        }
    }
}
