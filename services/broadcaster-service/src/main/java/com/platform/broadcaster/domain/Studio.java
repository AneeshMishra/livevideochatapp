package com.platform.broadcaster.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "studios")
@Getter
@Setter
@NoArgsConstructor
public class Studio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** User ID of the studio owner (from identity service). */
    @Column(nullable = false, unique = true)
    private UUID ownerId;

    /**
     * Default percentage the broadcaster keeps (e.g., 40 = 40%).
     * Individual broadcaster agreements can override this.
     */
    @Column(nullable = false)
    private int defaultRevenueSplitPercent = 40;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = Instant.now();
    }
}
