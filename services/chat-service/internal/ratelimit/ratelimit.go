package ratelimit

import (
	"context"
	"fmt"
	"strconv"
	"time"

	"github.com/redis/go-redis/v9"
)

// Limiter is a per-user, per-room sliding-window rate limiter backed by Redis.
type Limiter struct {
	rdb    *redis.Client
	max    int
	window time.Duration
}

func New(rdb *redis.Client, maxMsgs int, windowSec int) *Limiter {
	return &Limiter{
		rdb:    rdb,
		max:    maxMsgs,
		window: time.Duration(windowSec) * time.Second,
	}
}

// Allow returns true if the user is within rate limits.
// On Redis error it fails open (allows the message) to avoid blocking chat.
func (l *Limiter) Allow(ctx context.Context, userID, roomID string) (bool, error) {
	now := time.Now()
	key := fmt.Sprintf("ratelimit:chat:%s:%s", userID, roomID)
	member := strconv.FormatInt(now.UnixNano(), 10)
	windowStart := float64(now.Add(-l.window).UnixNano())

	pipe := l.rdb.Pipeline()
	pipe.ZRemRangeByScore(ctx, key, "0", strconv.FormatFloat(windowStart, 'f', 0, 64))
	pipe.ZAdd(ctx, key, redis.Z{Score: float64(now.UnixNano()), Member: member})
	cardCmd := pipe.ZCard(ctx, key)
	pipe.Expire(ctx, key, l.window*2)

	if _, err := pipe.Exec(ctx); err != nil {
		return true, err // fail open
	}

	return cardCmd.Val() <= int64(l.max), nil
}
