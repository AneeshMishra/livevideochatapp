package kafka

import (
	"context"
	"encoding/json"
	"time"

	kafkago "github.com/segmentio/kafka-go"
	"github.com/rs/zerolog/log"

	"github.com/platform/chat-service/internal/message"
)

// Broadcaster is implemented by ws.Hub — defined here to avoid import cycles.
type Broadcaster interface {
	BroadcastToRoom(roomID string, msg []byte)
}

// tipReceivedEvent mirrors TipEvent.TipReceived from the tipping-service Kafka payload.
type tipReceivedEvent struct {
	Type              string    `json:"type"`
	RoomID            string    `json:"roomId"`
	SenderID          string    `json:"senderId"`
	SenderDisplayName string    `json:"senderDisplayName"`
	TokenAmount       int64     `json:"tokenAmount"`
	Message           string    `json:"message"`
	OccurredAt        time.Time `json:"occurredAt"`
}

type tipGoalUpdatedEvent struct {
	Type            string    `json:"type"`
	RoomID          string    `json:"roomId"`
	GoalTitle       string    `json:"goalTitle"`
	ProgressPercent int       `json:"progressPercent"`
	OccurredAt      time.Time `json:"occurredAt"`
}

type tipGoalCompletedEvent struct {
	Type       string    `json:"type"`
	RoomID     string    `json:"roomId"`
	GoalTitle  string    `json:"goalTitle"`
	OccurredAt time.Time `json:"occurredAt"`
}

type Consumer struct {
	reader      *kafkago.Reader
	broadcaster Broadcaster
}

func NewConsumer(brokers []string, topic, groupID string, broadcaster Broadcaster) *Consumer {
	return &Consumer{
		reader: kafkago.NewReader(kafkago.ReaderConfig{
			Brokers:        brokers,
			Topic:          topic,
			GroupID:        groupID,
			MinBytes:       1,
			MaxBytes:       1 << 20, // 1 MiB
			CommitInterval: time.Second,
		}),
		broadcaster: broadcaster,
	}
}

// Run consumes tipping.events and fans out real-time notifications to rooms.
// Blocks until ctx is cancelled.
func (c *Consumer) Run(ctx context.Context) error {
	defer c.reader.Close()
	logger := log.With().Str("component", "kafka-consumer").Logger()

	for {
		m, err := c.reader.FetchMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			logger.Error().Err(err).Msg("fetch message")
			continue
		}
		c.dispatch(m.Value)
		if err := c.reader.CommitMessages(ctx, m); err != nil {
			logger.Error().Err(err).Msg("commit message")
		}
	}
}

func (c *Consumer) dispatch(data []byte) {
	var base struct {
		Type string `json:"type"`
	}
	if err := json.Unmarshal(data, &base); err != nil {
		return
	}
	switch base.Type {
	case "TIP_RECEIVED":
		var ev tipReceivedEvent
		if err := json.Unmarshal(data, &ev); err == nil {
			c.fanOut(ev.RoomID, &message.OutboundMessage{
				Type:        message.TypeTipNotification,
				RoomID:      ev.RoomID,
				UserID:      ev.SenderID,
				Username:    ev.SenderDisplayName,
				Content:     ev.Message,
				TokenAmount: ev.TokenAmount,
				Timestamp:   ev.OccurredAt,
			})
		}
	case "TIP_GOAL_UPDATED":
		var ev tipGoalUpdatedEvent
		if err := json.Unmarshal(data, &ev); err == nil {
			c.fanOut(ev.RoomID, &message.OutboundMessage{
				Type:      message.TypeGoalUpdate,
				RoomID:    ev.RoomID,
				GoalTitle: ev.GoalTitle,
				Progress:  ev.ProgressPercent,
				Timestamp: ev.OccurredAt,
			})
		}
	case "TIP_GOAL_COMPLETED":
		var ev tipGoalCompletedEvent
		if err := json.Unmarshal(data, &ev); err == nil {
			c.fanOut(ev.RoomID, &message.OutboundMessage{
				Type:      message.TypeGoalCompleted,
				RoomID:    ev.RoomID,
				GoalTitle: ev.GoalTitle,
				Timestamp: ev.OccurredAt,
			})
		}
	}
}

func (c *Consumer) fanOut(roomID string, out *message.OutboundMessage) {
	data, err := json.Marshal(out)
	if err != nil {
		return
	}
	c.broadcaster.BroadcastToRoom(roomID, data)
}
