package com.platform.userprofile.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "block_entries",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_block_pair",
        columnNames = {"blocker_id", "blocked_id"}
    ),
    indexes = {
        @Index(name = "idx_block_blocker", columnList = "blocker_id"),
        @Index(name = "idx_block_blocked", columnList = "blocked_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class BlockEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "blocker_id", nullable = false, updatable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false, updatable = false)
    private UUID blockedId;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static BlockEntry of(UUID blockerId, UUID blockedId, String reason) {
        BlockEntry b = new BlockEntry();
        b.blockerId = blockerId;
        b.blockedId = blockedId;
        b.reason = reason;
        return b;
    }
}
