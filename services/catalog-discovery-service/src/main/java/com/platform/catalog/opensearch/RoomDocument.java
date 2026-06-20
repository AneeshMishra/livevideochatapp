package com.platform.catalog.opensearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenSearch document shape for the catalog_rooms index.
 * Field names use snake_case to match the index mapping.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoomDocument {

    @JsonProperty("room_id")
    private String roomId;

    @JsonProperty("broadcaster_id")
    private String broadcasterId;

    @JsonProperty("broadcaster_username")
    private String broadcasterUsername;

    @JsonProperty("broadcaster_display_name")
    private String broadcasterDisplayName;

    @JsonProperty("broadcaster_avatar_url")
    private String broadcasterAvatarUrl;

    @JsonProperty("title")
    private String title;

    @JsonProperty("category")
    private String category;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("status")
    private String status;

    @JsonProperty("delivery_mode")
    private String deliveryMode;

    @JsonProperty("viewer_count")
    private long viewerCount;

    @JsonProperty("hls_playback_url")
    private String hlsPlaybackUrl;

    @JsonProperty("thumbnail_url")
    private String thumbnailUrl;

    @JsonProperty("is_featured")
    private boolean featured;

    @JsonProperty("stream_started_at")
    private String streamStartedAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getBroadcasterId() { return broadcasterId; }
    public void setBroadcasterId(String broadcasterId) { this.broadcasterId = broadcasterId; }

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

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDeliveryMode() { return deliveryMode; }
    public void setDeliveryMode(String deliveryMode) { this.deliveryMode = deliveryMode; }

    public long getViewerCount() { return viewerCount; }
    public void setViewerCount(long viewerCount) { this.viewerCount = viewerCount; }

    public String getHlsPlaybackUrl() { return hlsPlaybackUrl; }
    public void setHlsPlaybackUrl(String hlsPlaybackUrl) { this.hlsPlaybackUrl = hlsPlaybackUrl; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }

    public String getStreamStartedAt() { return streamStartedAt; }
    public void setStreamStartedAt(String streamStartedAt) { this.streamStartedAt = streamStartedAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
