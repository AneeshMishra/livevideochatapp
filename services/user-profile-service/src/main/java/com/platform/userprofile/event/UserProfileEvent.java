package com.platform.userprofile.event;

import java.time.Instant;
import java.util.UUID;

public sealed interface UserProfileEvent permits
        UserProfileEvent.ProfileCreated,
        UserProfileEvent.ProfileUpdated,
        UserProfileEvent.FollowedBroadcaster,
        UserProfileEvent.UnfollowedBroadcaster,
        UserProfileEvent.UserBlocked,
        UserProfileEvent.UserUnblocked {

    String eventType();
    Instant occurredAt();

    record ProfileCreated(
            UUID userId,
            String username,
            Instant occurredAt
    ) implements UserProfileEvent {
        public String eventType() { return "PROFILE_CREATED"; }
    }

    record ProfileUpdated(
            UUID userId,
            String username,
            Instant occurredAt
    ) implements UserProfileEvent {
        public String eventType() { return "PROFILE_UPDATED"; }
    }

    record FollowedBroadcaster(
            UUID followerId,
            UUID followeeId,
            Instant occurredAt
    ) implements UserProfileEvent {
        public String eventType() { return "FOLLOWED_BROADCASTER"; }
    }

    record UnfollowedBroadcaster(
            UUID followerId,
            UUID followeeId,
            Instant occurredAt
    ) implements UserProfileEvent {
        public String eventType() { return "UNFOLLOWED_BROADCASTER"; }
    }

    record UserBlocked(
            UUID blockerId,
            UUID blockedId,
            Instant occurredAt
    ) implements UserProfileEvent {
        public String eventType() { return "USER_BLOCKED"; }
    }

    record UserUnblocked(
            UUID blockerId,
            UUID blockedId,
            Instant occurredAt
    ) implements UserProfileEvent {
        public String eventType() { return "USER_UNBLOCKED"; }
    }
}
