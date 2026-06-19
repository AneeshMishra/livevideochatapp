package com.platform.userprofile.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.userprofile.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reacts to auth.events so profile creation is decoupled from the auth service.
 * On USER_REGISTERED: bootstraps a UserProfile row automatically.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEventConsumer {

    private final UserProfileService profileService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.auth-events}",
            groupId = "user-profile-service",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void onAuthEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String eventType = node.path("eventType").asText();

            if ("USER_REGISTERED".equals(eventType)) {
                UUID userId   = UUID.fromString(node.path("userId").asText());
                String username = node.path("username").asText();
                String email    = node.path("email").asText();

                profileService.initializeProfile(userId, username, email);
                log.info("Profile bootstrapped for user_id={} username={}", userId, username);
            }
        } catch (Exception ex) {
            // Log and continue — dead-letter or manual reconciliation handles persistent failures
            log.error("Failed to process auth event: {}", ex.getMessage(), ex);
        }
    }
}
