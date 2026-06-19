package kafka

import (
	"context"
	"encoding/json"
	"time"

	kafkago "github.com/segmentio/kafka-go"
	"github.com/rs/zerolog/log"
)

// StreamEvent is published to streaming.events.
// Consumed by: catalog/discovery service, notification service, analytics.
type StreamEvent struct {
	Type           string    `json:"type"`
	RoomID         string    `json:"roomId"`
	BroadcasterID  string    `json:"broadcasterId"`
	SessionID      string    `json:"sessionId,omitempty"`
	DeliveryMode   string    `json:"deliveryMode,omitempty"`
	ViewerCount    int64     `json:"viewerCount,omitempty"`
	HLSPlaybackURL string    `json:"hlsPlaybackUrl,omitempty"`
	OccurredAt     time.Time `json:"occurredAt"`
}

// Streaming event type constants.
const (
	EventStreamStarted       = "STREAM_STARTED"
	EventStreamEnded         = "STREAM_ENDED"
	EventStreamPromotedHLS   = "STREAM_PROMOTED_TO_HLS"
	EventStreamDemotedWebRTC = "STREAM_DEMOTED_TO_WEBRTC"
)

type Producer struct {
	writer *kafkago.Writer
}

func NewProducer(brokers []string, topic string) *Producer {
	return &Producer{
		writer: &kafkago.Writer{
			Addr:         kafkago.TCP(brokers...),
			Topic:        topic,
			Balancer:     &kafkago.Hash{}, // partition by roomId key
			BatchTimeout: 10 * time.Millisecond,
			RequiredAcks: kafkago.RequireOne,
		},
	}
}

func (p *Producer) Close() error { return p.writer.Close() }

func (p *Producer) PublishStreamStarted(ctx context.Context, roomID, broadcasterID, sessionID, hlsURL string) {
	p.publish(ctx, roomID, &StreamEvent{
		Type:           EventStreamStarted,
		RoomID:         roomID,
		BroadcasterID:  broadcasterID,
		SessionID:      sessionID,
		DeliveryMode:   "WEBRTC",
		HLSPlaybackURL: hlsURL,
		OccurredAt:     time.Now().UTC(),
	})
}

func (p *Producer) PublishStreamEnded(ctx context.Context, roomID, broadcasterID, sessionID string, peakViewers int64) {
	p.publish(ctx, roomID, &StreamEvent{
		Type:          EventStreamEnded,
		RoomID:        roomID,
		BroadcasterID: broadcasterID,
		SessionID:     sessionID,
		ViewerCount:   peakViewers,
		OccurredAt:    time.Now().UTC(),
	})
}

func (p *Producer) PublishPromotedToHLS(ctx context.Context, roomID, broadcasterID, hlsURL string, viewers int64) {
	p.publish(ctx, roomID, &StreamEvent{
		Type:           EventStreamPromotedHLS,
		RoomID:         roomID,
		BroadcasterID:  broadcasterID,
		DeliveryMode:   "LL_HLS",
		HLSPlaybackURL: hlsURL,
		ViewerCount:    viewers,
		OccurredAt:     time.Now().UTC(),
	})
}

func (p *Producer) PublishDemotedToWebRTC(ctx context.Context, roomID, broadcasterID string, viewers int64) {
	p.publish(ctx, roomID, &StreamEvent{
		Type:          EventStreamDemotedWebRTC,
		RoomID:        roomID,
		BroadcasterID: broadcasterID,
		DeliveryMode:  "WEBRTC",
		ViewerCount:   viewers,
		OccurredAt:    time.Now().UTC(),
	})
}

func (p *Producer) publish(ctx context.Context, key string, ev *StreamEvent) {
	data, err := json.Marshal(ev)
	if err != nil {
		log.Error().Err(err).Str("type", ev.Type).Msg("marshal stream event")
		return
	}
	if err := p.writer.WriteMessages(ctx, kafkago.Message{
		Key:   []byte(key),
		Value: data,
	}); err != nil {
		log.Error().Err(err).Str("type", ev.Type).Str("room_id", key).Msg("publish stream event")
	}
}
