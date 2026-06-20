package com.platform.tipping.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Events produced to tipping.events Kafka topic.
 * Consumed by:
 *  - chat/real-time service   → WebSocket fan-out to all viewers in the room (on-screen animation)
 *  - presence/catalog service → update tipper leaderboard
 *  - notification service     → "you received a tip" push to broadcaster
 */
public sealed interface TipEvent permits
        TipEvent.TipReceived,
        TipEvent.TipGoalUpdated,
        TipEvent.TipGoalCompleted,
        TipEvent.GiftSent {

    String eventType();
    UUID roomId();
    Instant occurredAt();

    /**
     * Fired for every successful tip — primary event for real-time animation trigger.
     * senderDisplayName is a denormalised snapshot so consumers don't need a profile lookup.
     */
    record TipReceived(
            UUID tipId,
            UUID roomId,
            UUID senderId,
            String senderDisplayName,
            UUID recipientId,
            long tokenAmount,
            String message,           // null if no message
            UUID tipMenuItemId,       // null if free-form tip
            String tipMenuItemTitle,  // null if free-form tip
            Instant occurredAt
    ) implements TipEvent {
        public String eventType() { return "TIP_RECEIVED"; }
    }

    /**
     * Fired on every tip that contributes to an active goal — drives the goal progress bar.
     */
    record TipGoalUpdated(
            UUID goalId,
            UUID roomId,
            UUID broadcasterId,
            String goalTitle,
            long targetTokens,
            long currentTokens,
            int progressPercent,
            Instant occurredAt
    ) implements TipEvent {
        public String eventType() { return "TIP_GOAL_UPDATED"; }
    }

    /**
     * Fired once when a goal's target is reached — triggers the completion animation.
     */
    record TipGoalCompleted(
            UUID goalId,
            UUID roomId,
            UUID broadcasterId,
            String goalTitle,
            long targetTokens,
            Instant occurredAt
    ) implements TipEvent {
        public String eventType() { return "TIP_GOAL_COMPLETED"; }
    }

    /**
     * Fired when a viewer sends a virtual animated gift.
     * animationType drives which on-screen animation the client renders.
     */
    record GiftSent(
            UUID giftSentId,
            UUID roomId,
            UUID senderId,
            String senderDisplayName,
            UUID recipientId,
            UUID giftTypeId,
            String giftTypeName,
            String animationType,
            long tokenAmount,
            String message,
            Instant occurredAt
    ) implements TipEvent {
        public String eventType() { return "GIFT_SENT"; }
    }
}
