package com.platform.broadcaster.config;

import com.platform.broadcaster.event.BroadcasterEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.broadcaster-events}")
    private String broadcasterEventsTopic;

    @Value("${app.kafka.topics.broadcaster-kyc-events}")
    private String kycEventsTopic;

    /** Topic with 12 partitions — keyed by broadcasterId, ensuring per-broadcaster ordering. */
    @Bean
    public NewTopic broadcasterEventsTopic() {
        return TopicBuilder.name(broadcasterEventsTopic)
            .partitions(12)
            .replicas(3)
            .config("retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000)) // 7 days
            .build();
    }

    /** KYC topic is sensitive — stricter retention and separate consumer ACLs. */
    @Bean
    public NewTopic kycEventsTopic() {
        return TopicBuilder.name(kycEventsTopic)
            .partitions(4)
            .replicas(3)
            .config("retention.ms", String.valueOf(90L * 24 * 60 * 60 * 1000)) // 90 days audit trail
            .build();
    }
}
