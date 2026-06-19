package com.platform.broadcaster.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "tip_menu_items")
@Getter
@Setter
@NoArgsConstructor
public class TipMenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcaster_id", nullable = false)
    private Broadcaster broadcaster;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(length = 300)
    private String description;

    /** Price in tokens. */
    @Column(nullable = false)
    private int tokenPrice;

    @Column(nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean active = true;
}
