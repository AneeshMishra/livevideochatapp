package com.platform.broadcaster.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class BroadcasterEventPublisher {

    private final KafkaTemplate<String, BroadcasterEvent> kafkaTemplate;

    @Value("${app.kafka.topics.broadcaster-events}")
    private String broadcasterEventsTopic;

    @Value("${app.kafka.topics.broadcaster-kyc-events}")
    private String kycEventsTopic;

    /**
     * Publishes a broadcaster domain event.
     * Key = broadcasterId so events for the same broadcaster are ordered within a partition.
     */
    public void publish(BroadcasterEvent event) {
        String topic = (event instanceof BroadcasterEvent.KycStatusChanged)
            ? kycEventsTopic
            : broadcasterEventsTopic;

        String key = event.broadcasterId().toString();
        CompletableFuture<SendResult<String, BroadcasterEvent>> future =
            kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event type={} broadcasterId={}: {}",
                    event.getClass().getSimpleName(), event.broadcasterId(), ex.getMessage());
            } else {
                log.debug("Published event type={} broadcasterId={} offset={}",
                    event.getClass().getSimpleName(), event.broadcasterId(),
                    result.getRecordMetadata().offset());
            }
        });
    }
}
