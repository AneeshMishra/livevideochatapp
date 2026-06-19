package provider

import (
	"context"
	"fmt"

	"github.com/aws/aws-sdk-go-v2/aws"
	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/ivs"
	ivstypes "github.com/aws/aws-sdk-go-v2/service/ivs/types"
)

// IVSProvider implements StreamingProvider using Amazon IVS.
// Phase 1 managed streaming — zero SFU ops.
//
// IVS channel delivers:
//   - Low-latency HLS (~3 s) via STANDARD channel type.
//   - Ultra-low-latency (< 300 ms) via REAL_TIME channel type (Stage API).
//
// For Phase 1 we use STANDARD channels with RTMP ingest.
// Phase 3 migration: switch to IVS Real-Time (Stages) or self-hosted LiveKit.
type IVSProvider struct {
	client *ivs.Client
}

func NewIVSProvider(ctx context.Context, region string) (*IVSProvider, error) {
	cfg, err := awsconfig.LoadDefaultConfig(ctx, awsconfig.WithRegion(region))
	if err != nil {
		return nil, fmt.Errorf("load AWS config: %w", err)
	}
	return &IVSProvider{client: ivs.NewFromConfig(cfg)}, nil
}

func (p *IVSProvider) CreateChannel(ctx context.Context, roomID, broadcasterID string) (*ProvisionResult, error) {
	// Create the IVS channel.
	chOut, err := p.client.CreateChannel(ctx, &ivs.CreateChannelInput{
		Name:        aws.String("room-" + roomID),
		Type:        ivstypes.ChannelTypeStandard,
		LatencyMode: ivstypes.ChannelLatencyModeLowLatency,
		Tags: map[string]string{
			"roomId":        roomID,
			"broadcasterId": broadcasterID,
		},
	})
	if err != nil {
		return nil, fmt.Errorf("IVS CreateChannel: %w", err)
	}
	ch := chOut.Channel
	sk := chOut.StreamKey

	// IVS returns the ingest endpoint domain; the RTMP path is always /app/.
	ingestURL := fmt.Sprintf("rtmps://%s:443/app/", aws.ToString(ch.IngestEndpoint))

	return &ProvisionResult{
		ChannelID:      aws.ToString(ch.Arn),
		IngestEndpoint: ingestURL,
		StreamKey:      aws.ToString(sk.Value),
		HLSPlaybackURL: aws.ToString(ch.PlaybackUrl),
	}, nil
}

func (p *IVSProvider) DeleteChannel(ctx context.Context, channelID string) error {
	// Delete stream keys first, then the channel.
	keysOut, err := p.client.ListStreamKeys(ctx, &ivs.ListStreamKeysInput{
		ChannelArn: aws.String(channelID),
	})
	if err == nil {
		for _, k := range keysOut.StreamKeys {
			p.client.DeleteStreamKey(ctx, &ivs.DeleteStreamKeyInput{Arn: k.Arn})
		}
	}
	_, err = p.client.DeleteChannel(ctx, &ivs.DeleteChannelInput{
		Arn: aws.String(channelID),
	})
	if err != nil {
		return fmt.Errorf("IVS DeleteChannel: %w", err)
	}
	return nil
}

func (p *IVSProvider) StopStream(ctx context.Context, channelID string) error {
	_, err := p.client.StopStream(ctx, &ivs.StopStreamInput{
		ChannelArn: aws.String(channelID),
	})
	if err != nil {
		return fmt.Errorf("IVS StopStream: %w", err)
	}
	return nil
}

func (p *IVSProvider) IsStreamActive(ctx context.Context, channelID string) (bool, error) {
	out, err := p.client.GetStream(ctx, &ivs.GetStreamInput{
		ChannelArn: aws.String(channelID),
	})
	if err != nil {
		// IVS returns an error when there is no active stream — treat as inactive.
		return false, nil
	}
	return out.Stream != nil && out.Stream.State == ivstypes.StreamStateLive, nil
}
