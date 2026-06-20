package com.platform.catalog.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.platform.catalog.domain.CatalogRoom;
import com.platform.catalog.opensearch.RoomDocument;

import java.util.Arrays;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RoomCardResponse {

    private String roomId;
    private String broadcasterId;
    private String broadcasterUsername;
    private String broadcasterDisplayName;
    private String broadcasterAvatarUrl;
    private String title;
    private String category;
    private List<String> tags;
    private String status;
    private String deliveryMode;
    private long viewerCount;
    private String hlsPlaybackUrl;
    private String thumbnailUrl;
    private boolean featured;
    private String streamStartedAt;

    // ── Factory methods ────────────────────────────────────────────────────────

    public static RoomCardResponse fromEntity(CatalogRoom room) {
        var r = new RoomCardResponse();
        r.roomId                = room.getId().toString();
        r.broadcasterId         = room.getBroadcasterId().toString();
        r.broadcasterUsername   = room.getBroadcasterUsername();
        r.broadcasterDisplayName = room.getBroadcasterDisplayName();
        r.broadcasterAvatarUrl  = room.getBroadcasterAvatarUrl();
        r.title                 = room.getTitle();
        r.category              = room.getCategory();
        r.tags                  = room.getTags() != null ? Arrays.asList(room.getTags()) : List.of();
        r.status                = room.getStatus().name();
        r.deliveryMode          = room.getDeliveryMode();
        r.viewerCount           = room.getViewerCount();
        r.hlsPlaybackUrl        = room.getHlsPlaybackUrl();
        r.thumbnailUrl          = room.getThumbnailUrl();
        r.featured              = room.isFeatured();
        r.streamStartedAt       = room.getStreamStartedAt() != null
                ? room.getStreamStartedAt().toString() : null;
        return r;
    }

    public static RoomCardResponse fromDocument(RoomDocument doc) {
        var r = new RoomCardResponse();
        r.roomId                = doc.getRoomId();
        r.broadcasterId         = doc.getBroadcasterId();
        r.broadcasterUsername   = doc.getBroadcasterUsername();
        r.broadcasterDisplayName = doc.getBroadcasterDisplayName();
        r.broadcasterAvatarUrl  = doc.getBroadcasterAvatarUrl();
        r.title                 = doc.getTitle();
        r.category              = doc.getCategory();
        r.tags                  = doc.getTags() != null ? doc.getTags() : List.of();
        r.status                = doc.getStatus();
        r.deliveryMode          = doc.getDeliveryMode();
        r.viewerCount           = doc.getViewerCount();
        r.hlsPlaybackUrl        = doc.getHlsPlaybackUrl();
        r.thumbnailUrl          = doc.getThumbnailUrl();
        r.featured              = doc.isFeatured();
        r.streamStartedAt       = doc.getStreamStartedAt();
        return r;
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public String getRoomId() { return roomId; }
    public String getBroadcasterId() { return broadcasterId; }
    public String getBroadcasterUsername() { return broadcasterUsername; }
    public String getBroadcasterDisplayName() { return broadcasterDisplayName; }
    public String getBroadcasterAvatarUrl() { return broadcasterAvatarUrl; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public List<String> getTags() { return tags; }
    public String getStatus() { return status; }
    public String getDeliveryMode() { return deliveryMode; }
    public long getViewerCount() { return viewerCount; }
    public String getHlsPlaybackUrl() { return hlsPlaybackUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public boolean isFeatured() { return featured; }
    public String getStreamStartedAt() { return streamStartedAt; }
}
