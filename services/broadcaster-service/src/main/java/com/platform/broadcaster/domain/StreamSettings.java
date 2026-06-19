package com.platform.broadcaster.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "stream_settings")
@Getter
@Setter
@NoArgsConstructor
public class StreamSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcaster_id", nullable = false, unique = true)
    private Broadcaster broadcaster;

    @Column(length = 200)
    private String title;

    /** Comma-separated tag slugs; stored denormalised for simplicity. */
    @Column(length = 500)
    private String tags;

    @Column(length = 100)
    private String category;

    /** Token price per minute for a private 1-on-1 show. */
    @Column(nullable = false)
    private int privateShowPricePerMinute = 30;

    /** Token price per minute for a group show ticket. */
    @Column(nullable = false)
    private int groupShowPricePerMinute = 10;

    /** Token price per minute for spy mode (watching a private show). */
    @Column(nullable = false)
    private int spyShowPricePerMinute = 6;

    @Column(nullable = false)
    private boolean recordingEnabled = false;

    /** Cam-to-cam only available to users of this level or higher (0 = all). */
    @Column(nullable = false)
    private int cam2camMinViewerLevel = 0;
}
