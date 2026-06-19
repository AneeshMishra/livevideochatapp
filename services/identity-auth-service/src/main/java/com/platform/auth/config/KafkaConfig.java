package com.platform.auth.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.converter.JsonMessageConverter;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.auth-events}")
    private String authEventsTopic;

    @Bean
    public NewTopic authEventsTopic() {
        return TopicBuilder.name(authEventsTopic)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public JsonMessageConverter jsonMessageConverter() {
        return new JsonMessageConverter();
    }
}
