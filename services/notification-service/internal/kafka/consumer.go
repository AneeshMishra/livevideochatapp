package kafka

import (
	"context"
	"time"

	kafkago "github.com/segmentio/kafka-go"
	"github.com/rs/zerolog/log"
	"golang.org/x/sync/errgroup"

	"github.com/platform/notification-service/internal/notification"
)

// Consumer reads from multiple Kafka topics and sends raw events to a shared channel.
type Consumer struct {
	readers []*kafkago.Reader
}

// topicGroup pairs a topic with a dedicated consumer group suffix so each topic
// gets its own committed offset, but all share the same base group ID.
type topicGroup struct {
	topic   string
	groupID string
}

func NewConsumer(brokers []string, groupID string, topics ...string) *Consumer {
	readers := make([]*kafkago.Reader, 0, len(topics))
	for _, topic := range topics {
		readers = append(readers, kafkago.NewReader(kafkago.ReaderConfig{
			Brokers:        brokers,
			Topic:          topic,
			GroupID:        groupID + "-" + sanitise(topic),
			MinBytes:       1,
			MaxBytes:       1 << 20,
			CommitInterval: time.Second,
			StartOffset:    kafkago.LastOffset,
		}))
	}
	return &Consumer{readers: readers}
}

// Run fans out goroutines, one per reader, all sending into the events channel.
// Blocks until ctx is cancelled.
func (c *Consumer) Run(ctx context.Context, events chan<- notification.RawEvent) error {
	g, gctx := errgroup.WithContext(ctx)
	for _, r := range c.readers {
		reader := r
		g.Go(func() error {
			return readLoop(gctx, reader, events)
		})
	}
	return g.Wait()
}

func (c *Consumer) Close() {
	for _, r := range c.readers {
		_ = r.Close()
	}
}

func readLoop(ctx context.Context, r *kafkago.Reader, out chan<- notification.RawEvent) error {
	topic := r.Config().Topic
	logger := log.With().Str("topic", topic).Logger()

	for {
		msg, err := r.FetchMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			logger.Error().Err(err).Msg("fetch message")
			continue
		}

		out <- notification.RawEvent{Topic: topic, Value: msg.Value}
		_ = r.CommitMessages(ctx, msg)
	}
}

func sanitise(topic string) string {
	out := make([]byte, len(topic))
	for i, b := range []byte(topic) {
		if b == '.' || b == '-' {
			out[i] = '_'
		} else {
			out[i] = b
		}
	}
	return string(out)
}
