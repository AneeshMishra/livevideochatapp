package kafka

import (
	"context"
	"encoding/json"
	"time"

	kafkago "github.com/segmentio/kafka-go"
	"github.com/rs/zerolog/log"
)

// ViewerCountHandler is called when a ROOM_COUNT_SNAPSHOT event arrives.
// Implemented by service.Orchestrator.
type ViewerCountHandler interface {
	UpdateViewerCount(ctx context.Context, roomID string, count int64)
}

// PresenceConsumer consumes presence.events and forwards viewer count updates
// to the orchestrator, which checks the promotion threshold.
type PresenceConsumer struct {
	reader  *kafkago.Reader
	handler ViewerCountHandler
}

func NewPresenceConsumer(brokers []string, topic, groupID string, handler ViewerCountHandler) *PresenceConsumer {
	return &PresenceConsumer{
		reader: kafkago.NewReader(kafkago.ReaderConfig{
			Brokers:        brokers,
			Topic:          topic,
			GroupID:        groupID,
			MinBytes:       1,
			MaxBytes:       1 << 20,
			CommitInterval: time.Second,
		}),
		handler: handler,
	}
}

func (c *PresenceConsumer) Run(ctx context.Context) error {
	defer c.reader.Close()
	logger := log.With().Str("component", "presence-consumer").Logger()

	for {
		m, err := c.reader.FetchMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			logger.Error().Err(err).Msg("fetch message")
			continue
		}

		c.dispatch(ctx, m.Value)

		if err := c.reader.CommitMessages(ctx, m); err != nil {
			logger.Error().Err(err).Msg("commit message")
		}
	}
}

func (c *PresenceConsumer) dispatch(ctx context.Context, data []byte) {
	var ev struct {
		Type   string `json:"type"`
		RoomID string `json:"roomId"`
		Count  int64  `json:"count"`
	}
	if err := json.Unmarshal(data, &ev); err != nil || ev.Type != "ROOM_COUNT_SNAPSHOT" {
		return
	}
	if ev.RoomID == "" {
		return
	}
	c.handler.UpdateViewerCount(ctx, ev.RoomID, ev.Count)
}
