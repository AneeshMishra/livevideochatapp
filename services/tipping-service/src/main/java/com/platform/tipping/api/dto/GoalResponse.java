package com.platform.tipping.api.dto;

import com.platform.tipping.domain.GoalStatus;
import com.platform.tipping.domain.TipGoal;

import java.time.Instant;
import java.util.UUID;

public record GoalResponse(
        UUID id,
        UUID broadcasterId,
        UUID roomId,
        String title,
        long targetTokens,
        long currentTokens,
        int progressPercent,
        long remainingTokens,
        GoalStatus status,
        Instant createdAt,
        Instant completedAt
) {
    public static GoalResponse from(TipGoal g) {
        return new GoalResponse(
                g.getId(), g.getBroadcasterId(), g.getRoomId(),
                g.getTitle(), g.getTargetTokens(), g.getCurrentTokens(),
                g.progressPercent(), g.remainingTokens(),
                g.getStatus(), g.getCreatedAt(), g.getCompletedAt());
    }
}
