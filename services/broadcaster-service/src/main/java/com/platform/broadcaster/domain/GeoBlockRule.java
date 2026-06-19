package com.platform.broadcaster.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
    name = "geo_block_rules",
    uniqueConstraints = @UniqueConstraint(columnNames = {"broadcaster_id", "country_code"})
)
@Getter
@Setter
@NoArgsConstructor
public class GeoBlockRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcaster_id", nullable = false)
    private Broadcaster broadcaster;

    /** ISO 3166-1 alpha-2 country code (e.g. "US", "GB"). */
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GeoBlockType type = GeoBlockType.BLOCK;
}
