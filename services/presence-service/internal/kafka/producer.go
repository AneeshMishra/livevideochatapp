package kafka

import (
	"context"
	"encoding/json"
	"time"

	kafkago "github.com/segmentio/kafka-go"
	"github.com/rs/zerolog/log"
)

// PresenceEvent is published to the presence.events Kafka topic.
// Consumers: catalog/discovery service (viewer counts), notification service (favorite is live).
type PresenceEvent struct {
	Type       string    `json:"type"`
	UserID     string    `json:"userId,omitempty"`
	RoomID     string    `json:"roomId,omitempty"`
	Count      int64     `json:"count,omitempty"`
	OccurredAt time.Time `json:"occurredAt"`
}

// Event type constants.
const (
	EventUserJoinedRoom     = "USER_JOINED_ROOM"
	EventUserLeftRoom       = "USER_LEFT_ROOM"
	EventRoomCountSnapshot  = "ROOM_COUNT_SNAPSHOT"
)

type Producer struct {
	writer *kafkago.Writer
	topic  string
}

func NewProducer(brokers []string, topic string) *Producer {
	return &Producer{
		writer: &kafkago.Writer{
			Addr:         kafkago.TCP(brokers...),
			Topic:        topic,
			Balancer:     &kafkago.Hash{}, // partition by key (roomId) for ordering
			BatchTimeout: 10 * time.Millisecond,
			RequiredAcks: kafkago.RequireOne,
		},
		topic: topic,
	}
}

func (p *Producer) Close() error {
	return p.writer.Close()
}

func (p *Producer) PublishUserJoined(ctx context.Context, userID, roomID string) {
	p.publish(ctx, roomID, &PresenceEvent{
		Type:       EventUserJoinedRoom,
		UserID:     userID,
		RoomID:     roomID,
		OccurredAt: time.Now().UTC(),
	})
}

func (p *Producer) PublishUserLeft(ctx context.Context, userID, roomID string) {
	p.publish(ctx, roomID, &PresenceEvent{
		Type:       EventUserLeftRoom,
		UserID:     userID,
		RoomID:     roomID,
		OccurredAt: time.Now().UTC(),
	})
}

// PublishRoomCount emits a periodic snapshot of viewer count for the catalog service.
func (p *Producer) PublishRoomCount(ctx context.Context, roomID string, count int64) {
	p.publish(ctx, roomID, &PresenceEvent{
		Type:       EventRoomCountSnapshot,
		RoomID:     roomID,
		Count:      count,
		OccurredAt: time.Now().UTC(),
	})
}

func (p *Producer) publish(ctx context.Context, key string, ev *PresenceEvent) {
	data, err := json.Marshal(ev)
	if err != nil {
		log.Error().Err(err).Str("type", ev.Type).Msg("marshal presence event")
		return
	}
	msg := kafkago.Message{
		Key:   []byte(key),
		Value: data,
	}
	if err := p.writer.WriteMessages(ctx, msg); err != nil {
		// Log and continue — presence events are informational, not money-critical.
		log.Error().Err(err).Str("type", ev.Type).Str("room_id", key).Msg("publish presence event")
	}
}
