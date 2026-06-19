package com.platform.userprofile.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserProfileEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.user-profile-events}")
    private String topic;

    public void publish(UserProfileEvent event) {
        String key = switch (event) {
            case UserProfileEvent.ProfileCreated e    -> e.userId().toString();
            case UserProfileEvent.ProfileUpdated e    -> e.userId().toString();
            case UserProfileEvent.FollowedBroadcaster e   -> e.followerId().toString();
            case UserProfileEvent.UnfollowedBroadcaster e -> e.followerId().toString();
            case UserProfileEvent.UserBlocked e       -> e.blockerId().toString();
            case UserProfileEvent.UserUnblocked e     -> e.blockerId().toString();
        };

        kafkaTemplate.send(topic, key, event)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} key={}: {}", event.eventType(), key, ex.getMessage());
                    }
                });
    }
}
