package com.platform.tipping.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tip_menu_items", indexes = {
    @Index(name = "idx_tmi_broadcaster_id", columnList = "broadcaster_id, active")
})
@Getter
@Setter
@NoArgsConstructor
public class TipMenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "broadcaster_id", nullable = false)
    private UUID broadcasterId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 300)
    private String description;

    @Column(name = "token_price", nullable = false)
    private long tokenPrice;

    // Display order in the menu (lower = first)
    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
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

    public static TipMenuItem create(UUID broadcasterId, String title, String description,
                                     long tokenPrice, int position) {
        TipMenuItem item = new TipMenuItem();
        item.broadcasterId = broadcasterId;
        item.title = title;
        item.description = description;
        item.tokenPrice = tokenPrice;
        item.position = position;
        return item;
    }
}
