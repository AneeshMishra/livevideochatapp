package com.platform.auth.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.auth-events}")
    private String authEventsTopic;

    public void publish(AuthEvent event) {
        String key = switch (event) {
            case AuthEvent.UserRegistered e -> e.userId().toString();
            case AuthEvent.UserLoggedIn e -> e.userId().toString();
            case AuthEvent.PasswordChanged e -> e.userId().toString();
            case AuthEvent.AccountLocked e -> e.userId().toString();
            case AuthEvent.AccountUnlocked e -> e.userId().toString();
            case AuthEvent.TokensRevoked e -> e.userId().toString();
        };

        kafkaTemplate.send(authEventsTopic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish auth event type={} key={}: {}",
                                event.eventType(), key, ex.getMessage());
                    } else {
                        log.debug("Published auth event type={} offset={}",
                                event.eventType(), result.getRecordMetadata().offset());
                    }
                });
    }
}
