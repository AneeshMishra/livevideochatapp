package com.platform.tipping.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tip_goals", indexes = {
    @Index(name = "idx_tg_broadcaster_room", columnList = "broadcaster_id, room_id, status")
})
@Getter
@Setter
@NoArgsConstructor
public class TipGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "broadcaster_id", nullable = false)
    private UUID broadcasterId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "target_tokens", nullable = false)
    private long targetTokens;

    @Column(name = "current_tokens", nullable = false)
    private long currentTokens = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalStatus status = GoalStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }

    public static TipGoal create(UUID broadcasterId, UUID roomId, String title, long targetTokens) {
        TipGoal g = new TipGoal();
        g.broadcasterId = broadcasterId;
        g.roomId = roomId;
        g.title = title;
        g.targetTokens = targetTokens;
        return g;
    }

    /**
     * Adds tip tokens to goal progress; marks COMPLETED if target is reached.
     * Must be called inside a transaction with a PESSIMISTIC_WRITE lock on this entity.
     * Returns true if the goal just crossed the threshold.
     */
    public boolean addProgress(long tokens) {
        if (status != GoalStatus.ACTIVE) return false;
        currentTokens = Math.min(currentTokens + tokens, targetTokens);
        if (currentTokens >= targetTokens) {
            status = GoalStatus.COMPLETED;
            completedAt = Instant.now();
            return true;
        }
        return false;
    }

    public long remainingTokens() {
        return Math.max(0, targetTokens - currentTokens);
    }

    public int progressPercent() {
        return (int) Math.min(100, (currentTokens * 100L) / targetTokens);
    }
}
