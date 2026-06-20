package com.platform.tipping.api.dto;

import java.util.UUID;

/**
 * One row in a tipper leaderboard — sender ID, display name, total tokens, tip/gift count.
 */
public record LeaderboardEntry(
        UUID senderId,
        String senderDisplayName,
        long totalTokens,
        long count,
        int rank
) {
    public static LeaderboardEntry fromRow(Object[] row, int rank) {
        return new LeaderboardEntry(
                (UUID) row[0],
                (String) row[1],
                ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue(),
                rank
        );
    }
}
