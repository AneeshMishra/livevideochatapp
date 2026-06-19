package com.platform.tipping.api.dto;

import com.platform.tipping.domain.Tip;
import com.platform.tipping.domain.TipStatus;

import java.time.Instant;
import java.util.UUID;

public record TipResponse(
        UUID id,
        UUID senderId,
        String senderDisplayName,
        UUID recipientId,
        UUID roomId,
        long tokenAmount,
        String message,
        UUID tipMenuItemId,
        TipStatus status,
        Instant createdAt,
        Instant completedAt
) {
    public static TipResponse from(Tip t) {
        return new TipResponse(
                t.getId(), t.getSenderId(), t.getSenderDisplayName(),
                t.getRecipientId(), t.getRoomId(), t.getTokenAmount(),
                t.getMessage(), t.getTipMenuItemId(),
                t.getStatus(), t.getCreatedAt(), t.getCompletedAt());
    }
}
