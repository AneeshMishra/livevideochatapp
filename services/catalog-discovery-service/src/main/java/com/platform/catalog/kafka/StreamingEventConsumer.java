package com.platform.catalog.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.catalog.kafka.dto.StreamingEvent;
import com.platform.catalog.service.CatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes streaming.events to keep room live/offline state in the catalog.
 * Events handled:
 *   STREAM_STARTED           → mark room LIVE, enrich broadcaster profile
 *   STREAM_ENDED             → mark room OFFLINE
 *   STREAM_PROMOTED_TO_HLS   → update delivery_mode to LL_HLS
 *   STREAM_DEMOTED_TO_WEBRTC → update delivery_mode to WEBRTC
 */
@Component
public class StreamingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(StreamingEventConsumer.class);

    private final CatalogService catalogService;
    private final ObjectMapper objectMapper;

    public StreamingEventConsumer(CatalogService catalogService, ObjectMapper objectMapper) {
        this.catalogService = catalogService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${kafka.topics.streaming}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onStreamingEvent(String payload, Acknowledgment ack) {
        try {
            StreamingEvent event = objectMapper.readValue(payload, StreamingEvent.class);
            if (event.getType() == null || event.getRoomId() == null) {
                log.warn("Ignoring malformed streaming event: {}", payload);
                ack.acknowledge();
                return;
            }

            switch (event.getType()) {
                case "STREAM_STARTED" ->
                    catalogService.handleStreamStarted(
                            event.getRoomId(),
                            event.getBroadcasterId(),
                            event.getHlsPlaybackUrl(),
                            null,   // title provided by broadcaster-service lookup
                            null,   // category — future: include in event
                            null    // tags
                    );
                case "STREAM_ENDED" ->
                    catalogService.handleStreamEnded(event.getRoomId());
                case "STREAM_PROMOTED_TO_HLS" ->
                    catalogService.handleDeliveryModeChange(event.getRoomId(), "LL_HLS");
                case "STREAM_DEMOTED_TO_WEBRTC" ->
                    catalogService.handleDeliveryModeChange(event.getRoomId(), "WEBRTC");
                default ->
                    log.debug("Skipping unhandled streaming event type: {}", event.getType());
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing streaming event: {}", e.getMessage(), e);
            // Acknowledge to avoid infinite retry loop on a poison message.
            ack.acknowledge();
        }
    }
}
