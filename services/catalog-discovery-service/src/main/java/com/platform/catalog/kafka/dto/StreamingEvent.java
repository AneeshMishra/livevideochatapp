package com.platform.catalog.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StreamingEvent {

    private String type;
    private String roomId;
    private String broadcasterId;
    private String sessionId;
    private String hlsPlaybackUrl;
    private String deliveryMode;
    private long viewerCount;
    private long peakViewerCount;
    private String timestamp;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getBroadcasterId() { return broadcasterId; }
    public void setBroadcasterId(String broadcasterId) { this.broadcasterId = broadcasterId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getHlsPlaybackUrl() { return hlsPlaybackUrl; }
    public void setHlsPlaybackUrl(String hlsPlaybackUrl) { this.hlsPlaybackUrl = hlsPlaybackUrl; }

    public String getDeliveryMode() { return deliveryMode; }
    public void setDeliveryMode(String deliveryMode) { this.deliveryMode = deliveryMode; }

    public long getViewerCount() { return viewerCount; }
    public void setViewerCount(long viewerCount) { this.viewerCount = viewerCount; }

    public long getPeakViewerCount() { return peakViewerCount; }
    public void setPeakViewerCount(long peakViewerCount) { this.peakViewerCount = peakViewerCount; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
