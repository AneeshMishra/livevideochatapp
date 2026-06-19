package pubsub

import (
	"context"
	"fmt"
	"strings"

	"github.com/redis/go-redis/v9"
)

const channelPrefix = "chat:room:"

type Client struct {
	rdb *redis.Client
}

func NewClient(redisURL string) (*Client, error) {
	opts, err := redis.ParseURL(redisURL)
	if err != nil {
		return nil, fmt.Errorf("parse redis URL: %w", err)
	}
	return &Client{rdb: redis.NewClient(opts)}, nil
}

func (c *Client) RedisClient() *redis.Client {
	return c.rdb
}

func (c *Client) Close() error {
	return c.rdb.Close()
}

func (c *Client) Ping(ctx context.Context) error {
	return c.rdb.Ping(ctx).Err()
}

// Publish sends a serialised OutboundMessage payload to all nodes subscribed to the room.
func (c *Client) Publish(ctx context.Context, roomID string, payload []byte) error {
	return c.rdb.Publish(ctx, channelPrefix+roomID, payload).Err()
}

// Subscribe pattern-subscribes to chat:room:* and calls handler for every message.
// Blocks until ctx is cancelled.
func (c *Client) Subscribe(ctx context.Context, handler func(roomID string, data []byte)) error {
	ps := c.rdb.PSubscribe(ctx, channelPrefix+"*")
	defer ps.Close()

	ch := ps.Channel()
	for {
		select {
		case <-ctx.Done():
			return nil
		case msg, ok := <-ch:
			if !ok {
				return nil
			}
			roomID := strings.TrimPrefix(msg.Channel, channelPrefix)
			handler(roomID, []byte(msg.Payload))
		}
	}
}
