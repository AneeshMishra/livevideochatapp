package store

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"

	"github.com/platform/streaming-orchestration-service/internal/domain"
)

const roomCacheTTL = 30 * time.Second

// roomCacheEntry is the fast-path room state stored in Redis.
type roomCacheEntry struct {
	Status         string `json:"status"`
	DeliveryMode   string `json:"deliveryMode"`
	PlaybackURL    string `json:"playbackUrl"`
	HLSPlaybackURL string `json:"hlsPlaybackUrl"`
	ViewerCount    int64  `json:"viewerCount"`
}

type RedisStore struct {
	rdb *redis.Client
}

func NewRedisStore(redisURL string) (*RedisStore, error) {
	opts, err := redis.ParseURL(redisURL)
	if err != nil {
		return nil, fmt.Errorf("parse redis URL: %w", err)
	}
	return &RedisStore{rdb: redis.NewClient(opts)}, nil
}

func (s *RedisStore) Close() error { return s.rdb.Close() }

func (s *RedisStore) Ping(ctx context.Context) error { return s.rdb.Ping(ctx).Err() }

// CacheRoom writes core room state to Redis for fast viewer lookups.
func (s *RedisStore) CacheRoom(ctx context.Context, r *domain.Room) error {
	entry := roomCacheEntry{
		Status:         string(r.Status),
		DeliveryMode:   string(r.DeliveryMode),
		PlaybackURL:    r.PlaybackURL(),
		HLSPlaybackURL: r.HLSPlaybackURL,
		ViewerCount:    r.ViewerCount,
	}
	data, err := json.Marshal(entry)
	if err != nil {
		return err
	}
	return s.rdb.Set(ctx, roomCacheKey(r.ID.String()), data, roomCacheTTL).Err()
}

// GetCachedRoom retrieves the fast-path room state.
// Returns nil, nil if the key is not in cache (cache miss — fall back to Postgres).
func (s *RedisStore) GetCachedRoom(ctx context.Context, roomID string) (*roomCacheEntry, error) {
	data, err := s.rdb.Get(ctx, roomCacheKey(roomID)).Bytes()
	if err == redis.Nil {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	var entry roomCacheEntry
	if err := json.Unmarshal(data, &entry); err != nil {
		return nil, err
	}
	return &entry, nil
}

// SetViewerCount updates the viewer count in the Redis cache and returns the
// updated entry so the caller can decide on promotion without a Postgres read.
func (s *RedisStore) SetViewerCount(ctx context.Context, roomID string, count int64) error {
	key := roomCacheKey(roomID)
	data, err := s.rdb.Get(ctx, key).Bytes()
	if err == redis.Nil {
		return nil // room not cached yet; orchestrator will update Postgres directly
	}
	if err != nil {
		return err
	}
	var entry roomCacheEntry
	if err := json.Unmarshal(data, &entry); err != nil {
		return err
	}
	entry.ViewerCount = count
	updated, _ := json.Marshal(entry)
	return s.rdb.Set(ctx, key, updated, roomCacheTTL).Err()
}

// SetDeliveryMode updates the cached delivery mode and playback URL after promotion.
func (s *RedisStore) SetDeliveryMode(ctx context.Context, roomID string, mode domain.DeliveryMode, playbackURL string) error {
	key := roomCacheKey(roomID)
	data, err := s.rdb.Get(ctx, key).Bytes()
	if err != nil {
		return nil // cache miss; will be refreshed on next read
	}
	var entry roomCacheEntry
	if err := json.Unmarshal(data, &entry); err != nil {
		return err
	}
	entry.DeliveryMode = string(mode)
	entry.PlaybackURL = playbackURL
	updated, _ := json.Marshal(entry)
	return s.rdb.Set(ctx, key, updated, roomCacheTTL).Err()
}

func (s *RedisStore) InvalidateRoom(ctx context.Context, roomID string) error {
	return s.rdb.Del(ctx, roomCacheKey(roomID)).Err()
}

func roomCacheKey(roomID string) string { return "stream:room:" + roomID }
