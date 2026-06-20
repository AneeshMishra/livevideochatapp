package com.platform.catalog.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.catalog.kafka.dto.PresenceEvent;
import com.platform.catalog.service.CatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes presence.events to keep viewer counts up to date in the catalog.
 * Only ROOM_COUNT_SNAPSHOT events are acted upon; joins/leaves are ignored.
 */
@Component
public class PresenceEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PresenceEventConsumer.class);

    private final CatalogService catalogService;
    private final ObjectMapper objectMapper;

    public PresenceEventConsumer(CatalogService catalogService, ObjectMapper objectMapper) {
        this.catalogService = catalogService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${kafka.topics.presence}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPresenceEvent(String payload, Acknowledgment ack) {
        try {
            PresenceEvent event = objectMapper.readValue(payload, PresenceEvent.class);

            if ("ROOM_COUNT_SNAPSHOT".equals(event.getType()) && event.getRoomId() != null) {
                catalogService.handleViewerCountUpdate(event.getRoomId(), event.getCount());
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing presence event: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }
}
