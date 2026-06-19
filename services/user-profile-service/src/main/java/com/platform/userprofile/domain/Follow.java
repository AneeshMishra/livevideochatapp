package com.platform.userprofile.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "follows",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_follows_pair",
        columnNames = {"follower_id", "followee_id"}
    ),
    indexes = {
        @Index(name = "idx_follows_follower", columnList = "follower_id"),
        @Index(name = "idx_follows_followee", columnList = "followee_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The viewer who clicked "Follow"
    @Column(name = "follower_id", nullable = false, updatable = false)
    private UUID followerId;

    // The broadcaster being followed (their userId)
    @Column(name = "followee_id", nullable = false, updatable = false)
    private UUID followeeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static Follow of(UUID followerId, UUID followeeId) {
        Follow f = new Follow();
        f.followerId = followerId;
        f.followeeId = followeeId;
        return f;
    }
}
