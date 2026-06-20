package com.platform.catalog.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "catalog_rooms")
public class CatalogRoom {

    @Id
    private UUID id;

    @Column(name = "broadcaster_id", nullable = false)
    private UUID broadcasterId;

    @Column(name = "broadcaster_username", nullable = false, length = 100)
    private String broadcasterUsername;

    @Column(name = "broadcaster_display_name", length = 200)
    private String broadcasterDisplayName;

    @Column(name = "broadcaster_avatar_url")
    private String broadcasterAvatarUrl;

    @Column(nullable = false, length = 500)
    private String title = "";

    @Column(length = 100)
    private String category;

    // Hibernate 6 + PostgreSQL: text[] mapped directly
    @Column(columnDefinition = "text[]")
    private String[] tags = new String[0];

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status = RoomStatus.OFFLINE;

    @Column(name = "delivery_mode", nullable = false, length = 20)
    private String deliveryMode = "WEBRTC";

    @Column(name = "viewer_count", nullable = false)
    private long viewerCount = 0;

    @Column(name = "peak_viewer_count", nullable = false)
    private long peakViewerCount = 0;

    @Column(name = "hls_playback_url")
    private String hlsPlaybackUrl;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "is_featured", nullable = false)
    private boolean featured = false;

    @Column(name = "geo_block_countries", columnDefinition = "text[]")
    private String[] geoBlockCountries = new String[0];

    @Column(name = "stream_started_at")
    private OffsetDateTime streamStartedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getBroadcasterId() { return broadcasterId; }
    public void setBroadcasterId(UUID broadcasterId) { this.broadcasterId = broadcasterId; }

    public String getBroadcasterUsername() { return broadcasterUsername; }
    public void setBroadcasterUsername(String broadcasterUsername) { this.broadcasterUsername = broadcasterUsername; }

    public String getBroadcasterDisplayName() { return broadcasterDisplayName; }
    public void setBroadcasterDisplayName(String broadcasterDisplayName) { this.broadcasterDisplayName = broadcasterDisplayName; }

    public String getBroadcasterAvatarUrl() { return broadcasterAvatarUrl; }
    public void setBroadcasterAvatarUrl(String broadcasterAvatarUrl) { this.broadcasterAvatarUrl = broadcasterAvatarUrl; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }

    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) { this.status = status; }

    public String getDeliveryMode() { return deliveryMode; }
    public void setDeliveryMode(String deliveryMode) { this.deliveryMode = deliveryMode; }

    public long getViewerCount() { return viewerCount; }
    public void setViewerCount(long viewerCount) { this.viewerCount = viewerCount; }

    public long getPeakViewerCount() { return peakViewerCount; }
    public void setPeakViewerCount(long peakViewerCount) { this.peakViewerCount = peakViewerCount; }

    public String getHlsPlaybackUrl() { return hlsPlaybackUrl; }
    public void setHlsPlaybackUrl(String hlsPlaybackUrl) { this.hlsPlaybackUrl = hlsPlaybackUrl; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }

    public String[] getGeoBlockCountries() { return geoBlockCountries; }
    public void setGeoBlockCountries(String[] geoBlockCountries) { this.geoBlockCountries = geoBlockCountries; }

    public OffsetDateTime getStreamStartedAt() { return streamStartedAt; }
    public void setStreamStartedAt(OffsetDateTime streamStartedAt) { this.streamStartedAt = streamStartedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
