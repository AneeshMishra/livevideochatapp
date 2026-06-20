package com.platform.kyc.config;

import com.platform.kyc.event.KycEvent;
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

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.topics.kyc-events}")
    private String kycEventsTopic;

    @Bean
    public NewTopic kycEventsTopic() {
        return TopicBuilder.name(kycEventsTopic)
            .partitions(4)
            .replicas(1)
            .build();
    }

    @Bean
    public ProducerFactory<String, KycEvent> kycProducerFactory() {
        Map<String, Object> props = Map.of(
            org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class,
            org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class,
            org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG, "all",
            org.apache.kafka.clients.producer.ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
            JsonSerializer.ADD_TYPE_INFO_HEADERS, false
        );
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, KycEvent> kafkaTemplate() {
        return new KafkaTemplate<>(kycProducerFactory());
    }
}
