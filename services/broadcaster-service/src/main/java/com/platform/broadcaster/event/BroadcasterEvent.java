package com.platform.broadcaster.event;

import com.platform.broadcaster.domain.BroadcasterStatus;
import com.platform.broadcaster.domain.KycStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed hierarchy of domain events published to the broadcaster.events Kafka topic.
 * Consumers (catalog, presence, moderation, notification) subscribe to this topic.
 */
public sealed interface BroadcasterEvent {

    UUID broadcasterId();
    Instant occurredAt();

    record ProfileUpdated(
        UUID broadcasterId,
        UUID userId,
        String displayName,
        String avatarUrl,
        Instant occurredAt
    ) implements BroadcasterEvent {}

    record StatusChanged(
        UUID broadcasterId,
        UUID userId,
        BroadcasterStatus previousStatus,
        BroadcasterStatus newStatus,
        Instant occurredAt
    ) implements BroadcasterEvent {}

    record StreamSettingsUpdated(
        UUID broadcasterId,
        String title,
        String tags,
        String category,
        Instant occurredAt
    ) implements BroadcasterEvent {}

    record KycStatusChanged(
        UUID broadcasterId,
        UUID userId,
        KycStatus previousStatus,
        KycStatus newStatus,
        Instant occurredAt
    ) implements BroadcasterEvent {}

    record GeoBlockRulesUpdated(
        UUID broadcasterId,
        Instant occurredAt
    ) implements BroadcasterEvent {}
}
