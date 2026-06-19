package com.platform.broadcaster.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "broadcasters")
@Getter
@Setter
@NoArgsConstructor
public class Broadcaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Immutable link to the user record in the identity service. */
    @Column(nullable = false, unique = true, updatable = false)
    private UUID userId;

    /** Null when broadcaster is independent (not managed by a studio). */
    @Column
    private UUID studioId;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BroadcasterStatus status = BroadcasterStatus.OFFLINE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus kycStatus = KycStatus.PENDING;

    /**
     * Encrypted S3 reference to the 2257 / KYC document bundle.
     * Never log or expose this value outside the KYC service.
     */
    @Column(length = 1000)
    private String kycDocumentRef;

    /**
     * Percentage of token earnings the broadcaster keeps (e.g., 50 = 50%).
     * Studio agreements may set a lower value; independent broadcasters default to 50.
     */
    @Column(nullable = false)
    private int revenueSplitPercent = 50;

    @OneToOne(mappedBy = "broadcaster", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private StreamSettings streamSettings;

    @OneToMany(mappedBy = "broadcaster", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<TipMenuItem> tipMenuItems = new ArrayList<>();

    @OneToMany(mappedBy = "broadcaster", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<GeoBlockRule> geoBlockRules = new ArrayList<>();

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

    // --- domain helpers ---

    public boolean isLive() {
        return status == BroadcasterStatus.ONLINE
            || status == BroadcasterStatus.PRIVATE
            || status == BroadcasterStatus.GROUP;
    }

    public boolean isKycApproved() {
        return kycStatus == KycStatus.APPROVED;
    }
}
