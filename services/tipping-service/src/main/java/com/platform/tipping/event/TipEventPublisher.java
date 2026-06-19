package com.platform.tipping.event;

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
public class TipEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.tipping-events:tipping.events}")
    private String tippingEventsTopic;

    /**
     * Publishes to tipping.events, partitioned by roomId so all events for a room
     * land on the same partition — preserving order for the animation queue.
     */
    public void publish(TipEvent event) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventType", event.eventType());
            envelope.put("occurredAt", event.occurredAt().toString());
            envelope.put("data", event);

            String payload = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(tippingEventsTopic, event.roomId().toString(), payload);

            log.info("Published tip event type={} roomId={}", event.eventType(), event.roomId());
        } catch (JsonProcessingException e) {
            // Log but don't fail the tip — the wallet transfer already succeeded.
            // A separate reconciliation job can re-publish missed events.
            log.error("Failed to serialize tip event type={} — tip still completed", event.eventType(), e);
        }
    }
}
