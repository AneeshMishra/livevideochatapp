package com.platform.tipping.api.dto;

import com.platform.tipping.domain.GiftSent;

import java.time.Instant;
import java.util.UUID;

public record GiftSentResponse(
        UUID id,
        UUID roomId,
        UUID senderId,
        String senderDisplayName,
        UUID recipientId,
        UUID giftTypeId,
        String giftTypeName,
        String animationType,
        long tokenAmount,
        String message,
        Instant createdAt
) {
    public static GiftSentResponse from(GiftSent g) {
        return new GiftSentResponse(
                g.getId(), g.getRoomId(),
                g.getSenderId(), g.getSenderDisplayName(),
                g.getRecipientId(), g.getGiftTypeId(),
                g.getGiftTypeName(), g.getAnimationType(),
                g.getTokenAmount(), g.getMessage(), g.getCreatedAt()
        );
    }
}
