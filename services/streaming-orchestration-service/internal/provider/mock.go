package provider

import (
	"context"
	"fmt"
)

// MockProvider is used for local development and CI.
// It returns deterministic fake URLs without requiring AWS credentials.
type MockProvider struct{}

func NewMockProvider() *MockProvider { return &MockProvider{} }

func (p *MockProvider) CreateChannel(_ context.Context, roomID, _ string) (*ProvisionResult, error) {
	short := roomID
	if len(short) > 8 {
		short = short[:8]
	}
	return &ProvisionResult{
		ChannelID:      "mock-channel-" + roomID,
		IngestEndpoint: "rtmps://mock-ingest.local:443/app/",
		StreamKey:      fmt.Sprintf("mock-sk-%s", short),
		HLSPlaybackURL: fmt.Sprintf("https://mock-playback.local/hls/%s/index.m3u8", roomID),
	}, nil
}

func (p *MockProvider) DeleteChannel(_ context.Context, _ string) error { return nil }
func (p *MockProvider) StopStream(_ context.Context, _ string) error    { return nil }
func (p *MockProvider) IsStreamActive(_ context.Context, _ string) (bool, error) {
	return true, nil
}
