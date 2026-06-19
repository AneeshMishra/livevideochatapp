package domain

import (
	"time"

	"github.com/google/uuid"
)

type RoomStatus string

const (
	RoomStatusOffline RoomStatus = "OFFLINE"
	RoomStatusLive    RoomStatus = "LIVE"
)

// DeliveryMode controls how video is delivered to viewers.
// WEBRTC  — low-latency SFU path (Amazon IVS real-time or LiveKit Cloud).
// LL_HLS  — CDN-delivered LL-HLS for large rooms (> promotion threshold).
type DeliveryMode string

const (
	DeliveryModeWebRTC DeliveryMode = "WEBRTC"
	DeliveryModeHLS    DeliveryMode = "LL_HLS"
)

type Provider string

const (
	ProviderIVS      Provider = "IVS"
	ProviderLiveKit  Provider = "LIVEKIT_CLOUD"
	ProviderMock     Provider = "MOCK"
)

// Room is the core aggregate for a broadcaster's live room.
type Room struct {
	ID                 uuid.UUID
	BroadcasterID      uuid.UUID
	Title              string
	Status             RoomStatus
	DeliveryMode       DeliveryMode
	IngestEndpoint     string   // rtmps://... endpoint the broadcaster sends RTMP to
	StreamKey          string   // RTMP stream key (treat as secret)
	HLSPlaybackURL     string   // HLS/LL-HLS URL for viewers (always present with IVS)
	WebRTCPlaybackURL  string   // WebRTC room URL (Phase 3+ with self-hosted SFU)
	Provider           Provider
	ProviderChannelID  string   // IVS channel ARN or LiveKit room ID
	ViewerCount        int64
	PeakViewerCount    int64
	PromotionThreshold int64    // viewer count above which delivery switches to LL_HLS
	CreatedAt          time.Time
	UpdatedAt          time.Time
	Version            int64    // optimistic locking column
}

// PlaybackURL returns the active delivery URL for a viewer based on current mode.
func (r *Room) PlaybackURL() string {
	if r.DeliveryMode == DeliveryModeHLS || r.WebRTCPlaybackURL == "" {
		return r.HLSPlaybackURL
	}
	return r.WebRTCPlaybackURL
}

// StreamSession records a single live session (start → stop lifecycle).
type StreamSession struct {
	ID            uuid.UUID
	RoomID        uuid.UUID
	BroadcasterID uuid.UUID
	Status        string // "ACTIVE" | "ENDED"
	StartedAt     time.Time
	EndedAt       *time.Time
	PeakViewers   int64
}

// ErrRoomNotFound is returned when a room UUID doesn't exist.
type ErrRoomNotFound struct{ ID uuid.UUID }

func (e ErrRoomNotFound) Error() string { return "room not found: " + e.ID.String() }

// ErrNotAuthorized is returned when the caller doesn't own the room.
type ErrNotAuthorized struct{}

func (e ErrNotAuthorized) Error() string { return "not authorized to manage this room" }

// ErrRoomAlreadyLive is returned when start is called on an already-live room.
type ErrRoomAlreadyLive struct{}

func (e ErrRoomAlreadyLive) Error() string { return "room is already live" }
