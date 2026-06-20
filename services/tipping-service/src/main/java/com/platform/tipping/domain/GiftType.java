package com.platform.tipping.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gift_types")
@Getter
@Setter
@NoArgsConstructor
public class GiftType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(length = 500)
    private String description;

    @Column(name = "icon_url")
    private String iconUrl;

    /** NONE | CONFETTI | FIREWORKS | HEARTS | CUSTOM */
    @Column(name = "animation_type", nullable = false, length = 50)
    private String animationType = "NONE";

    @Column(name = "token_price", nullable = false)
    private long tokenPrice;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }
}
