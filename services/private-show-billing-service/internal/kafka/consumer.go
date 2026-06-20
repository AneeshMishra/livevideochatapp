package kafka

import (
	"context"
	"encoding/json"
	"time"

	kafkago "github.com/segmentio/kafka-go"
	"github.com/rs/zerolog/log"
)

// StreamingEvent is the subset of streaming.events we care about.
type StreamingEvent struct {
	Type   string `json:"type"`
	RoomID string `json:"roomId"`
}

// StreamEndedHandler is called when a STREAM_ENDED event is received.
type StreamEndedHandler func(ctx context.Context, roomID string)

type Consumer struct {
	reader  *kafkago.Reader
	handler StreamEndedHandler
}

func NewConsumer(brokers []string, topic, groupID string, handler StreamEndedHandler) *Consumer {
	return &Consumer{
		reader: kafkago.NewReader(kafkago.ReaderConfig{
			Brokers:        brokers,
			Topic:          topic,
			GroupID:        groupID,
			MinBytes:       1,
			MaxBytes:       1 << 20, // 1 MB
			CommitInterval: time.Second,
			StartOffset:    kafkago.LastOffset,
		}),
		handler: handler,
	}
}

func (c *Consumer) Run(ctx context.Context) error {
	logger := log.With().Str("component", "streaming-consumer").Logger()
	for {
		msg, err := c.reader.FetchMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			logger.Error().Err(err).Msg("fetch streaming event")
			continue
		}

		var ev StreamingEvent
		if err := json.Unmarshal(msg.Value, &ev); err != nil {
			logger.Warn().Err(err).Msg("unmarshal streaming event")
			_ = c.reader.CommitMessages(ctx, msg)
			continue
		}

		if ev.Type == "STREAM_ENDED" && ev.RoomID != "" {
			logger.Info().Str("room_id", ev.RoomID).Msg("stream ended — terminating private shows")
			c.handler(ctx, ev.RoomID)
		}

		_ = c.reader.CommitMessages(ctx, msg)
	}
}

func (c *Consumer) Close() error {
	return c.reader.Close()
}
