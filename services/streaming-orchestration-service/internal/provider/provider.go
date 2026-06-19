package provider

import "context"

// ProvisionResult is returned when a streaming channel is created.
type ProvisionResult struct {
	ChannelID      string // provider-specific ID (IVS channel ARN, LiveKit room name)
	IngestEndpoint string // rtmps://{host}:443/app/
	StreamKey      string // appended to ingest URL; treat as secret
	HLSPlaybackURL string // viewers use this URL (HLS/LL-HLS)
}

// StreamingProvider abstracts the underlying media infrastructure.
// Phase 1: Amazon IVS (managed). Phase 3+: self-hosted LiveKit.
type StreamingProvider interface {
	// CreateChannel provisions a streaming channel for a room.
	CreateChannel(ctx context.Context, roomID, broadcasterID string) (*ProvisionResult, error)

	// DeleteChannel deprovisions the channel (called on room deletion or cleanup).
	DeleteChannel(ctx context.Context, channelID string) error

	// StopStream terminates an active stream on the channel (broadcaster went offline).
	StopStream(ctx context.Context, channelID string) error

	// IsStreamActive returns true if the provider currently sees an active stream.
	IsStreamActive(ctx context.Context, channelID string) (bool, error)
}
