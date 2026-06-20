package kafka

import (
	"context"
	"encoding/json"
	"time"

	"github.com/google/uuid"
	kafkago "github.com/segmentio/kafka-go"
	"github.com/rs/zerolog/log"
)

const (
	EventPrivateShowStarted          = "PRIVATE_SHOW_STARTED"
	EventPrivateShowTickBilled       = "PRIVATE_SHOW_TICK_BILLED"
	EventPrivateShowEnded            = "PRIVATE_SHOW_ENDED"
	EventPrivateShowInsufficientFunds = "PRIVATE_SHOW_INSUFFICIENT_FUNDS"
	EventPrivateShowPaused           = "PRIVATE_SHOW_PAUSED"
	EventPrivateShowResumed          = "PRIVATE_SHOW_RESUMED"
)

type PrivateShowEvent struct {
	Type          string    `json:"type"`
	SessionID     uuid.UUID `json:"sessionId"`
	ViewerID      uuid.UUID `json:"viewerId"`
	BroadcasterID uuid.UUID `json:"broadcasterId"`
	RoomID        uuid.UUID `json:"roomId"`
	ShowType      string    `json:"showType,omitempty"`
	RatePerMinute int64     `json:"ratePerMinute,omitempty"`
	TokensCharged int64     `json:"tokensCharged,omitempty"`
	MinuteNumber  int       `json:"minuteNumber,omitempty"`
	BilledMinutes int       `json:"billedMinutes,omitempty"`
	TotalTokens   int64     `json:"totalTokens,omitempty"`
	EndReason     string    `json:"endReason,omitempty"`
	OccurredAt    time.Time `json:"occurredAt"`
}

type Producer struct {
	writer *kafkago.Writer
}

func NewProducer(brokers []string, topic string) *Producer {
	return &Producer{
		writer: &kafkago.Writer{
			Addr:         kafkago.TCP(brokers...),
			Topic:        topic,
			Balancer:     &kafkago.Hash{},
			BatchTimeout: 10 * time.Millisecond,
			RequiredAcks: kafkago.RequireOne,
		},
	}
}

func (p *Producer) Close() error {
	return p.writer.Close()
}

func (p *Producer) PublishStarted(ctx context.Context, ev *PrivateShowEvent) {
	ev.Type = EventPrivateShowStarted
	p.publish(ctx, ev)
}

func (p *Producer) PublishTickBilled(ctx context.Context, ev *PrivateShowEvent) {
	ev.Type = EventPrivateShowTickBilled
	p.publish(ctx, ev)
}

func (p *Producer) PublishEnded(ctx context.Context, ev *PrivateShowEvent) {
	ev.Type = EventPrivateShowEnded
	p.publish(ctx, ev)
}

func (p *Producer) PublishInsufficientFunds(ctx context.Context, ev *PrivateShowEvent) {
	ev.Type = EventPrivateShowInsufficientFunds
	p.publish(ctx, ev)
}

func (p *Producer) PublishPaused(ctx context.Context, ev *PrivateShowEvent) {
	ev.Type = EventPrivateShowPaused
	p.publish(ctx, ev)
}

func (p *Producer) PublishResumed(ctx context.Context, ev *PrivateShowEvent) {
	ev.Type = EventPrivateShowResumed
	p.publish(ctx, ev)
}

func (p *Producer) publish(ctx context.Context, ev *PrivateShowEvent) {
	if ev.OccurredAt.IsZero() {
		ev.OccurredAt = time.Now().UTC()
	}
	data, err := json.Marshal(ev)
	if err != nil {
		log.Error().Err(err).Str("type", ev.Type).Msg("marshal private-show event")
		return
	}
	msg := kafkago.Message{
		Key:   []byte(ev.SessionID.String()),
		Value: data,
	}
	if err := p.writer.WriteMessages(ctx, msg); err != nil {
		log.Error().Err(err).Str("type", ev.Type).Str("session_id", ev.SessionID.String()).
			Msg("publish private-show event")
	}
}
