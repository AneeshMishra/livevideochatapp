package redis

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	rdb "github.com/redis/go-redis/v9"
)

const (
	dedupPrefix      = "notif:dedup:"
	rateLimitPush    = "notif:rl:push:"
	rateLimitEmail   = "notif:rl:email:"
	followersPrefix  = "notif:followers:"
	broadcasterName  = "notif:bname:"
)

type Store struct {
	client *rdb.Client
}

func NewStore(client *rdb.Client) *Store {
	return &Store{client: client}
}

func (s *Store) Ping(ctx context.Context) error {
	return s.client.Ping(ctx).Err()
}

// ── Deduplication ─────────────────────────────────────────────────────────────

// IsDuplicate returns true if this (userID, dedupeKey) combo was already processed.
// On first call for a key it sets the flag and returns false.
func (s *Store) IsDuplicate(ctx context.Context, userID uuid.UUID, dedupeKey string) (bool, error) {
	key := dedupPrefix + userID.String() + ":" + dedupeKey
	set, err := s.client.SetNX(ctx, key, "1", 2*time.Hour).Result()
	if err != nil {
		return false, err
	}
	return !set, nil // SetNX returns true when key was newly set (not a duplicate)
}

// ── Rate limiting ─────────────────────────────────────────────────────────────

// CheckAndIncrPush increments the push counter for this user and returns true if
// the send is allowed (under the hourly cap). Critical notifications bypass this.
func (s *Store) CheckAndIncrPush(ctx context.Context, userID uuid.UUID, maxPerHour int) (bool, error) {
	return s.checkAndIncr(ctx, rateLimitPush+userID.String(), time.Hour, maxPerHour)
}

// CheckAndIncrEmail increments the email counter and returns true if allowed.
func (s *Store) CheckAndIncrEmail(ctx context.Context, userID uuid.UUID, maxPerDay int) (bool, error) {
	return s.checkAndIncr(ctx, rateLimitEmail+userID.String(), 24*time.Hour, maxPerDay)
}

func (s *Store) checkAndIncr(ctx context.Context, key string, ttl time.Duration, max int) (bool, error) {
	pipe := s.client.TxPipeline()
	incrCmd := pipe.Incr(ctx, key)
	pipe.Expire(ctx, key, ttl)
	if _, err := pipe.Exec(ctx); err != nil {
		return false, err
	}
	return incrCmd.Val() <= int64(max), nil
}

// ── Follower set (built from FOLLOWED_BROADCASTER Kafka events) ───────────────

func (s *Store) AddFollower(ctx context.Context, broadcasterID, followerID uuid.UUID) error {
	key := followersPrefix + broadcasterID.String()
	return s.client.SAdd(ctx, key, followerID.String()).Err()
}

func (s *Store) RemoveFollower(ctx context.Context, broadcasterID, followerID uuid.UUID) error {
	key := followersPrefix + broadcasterID.String()
	return s.client.SRem(ctx, key, followerID.String()).Err()
}

// GetFollowerIDs returns all follower UUIDs for a broadcaster (from the in-memory set).
func (s *Store) GetFollowerIDs(ctx context.Context, broadcasterID uuid.UUID) ([]uuid.UUID, error) {
	key := followersPrefix + broadcasterID.String()
	members, err := s.client.SMembers(ctx, key).Result()
	if err != nil {
		return nil, err
	}
	out := make([]uuid.UUID, 0, len(members))
	for _, m := range members {
		if uid, err := uuid.Parse(m); err == nil {
			out = append(out, uid)
		}
	}
	return out, nil
}

// ── Broadcaster display-name cache ────────────────────────────────────────────

func (s *Store) CacheBroadcasterName(ctx context.Context, broadcasterID uuid.UUID, name string) {
	key := broadcasterName + broadcasterID.String()
	_ = s.client.Set(ctx, key, name, time.Hour).Err()
}

func (s *Store) GetCachedBroadcasterName(ctx context.Context, broadcasterID uuid.UUID) (string, bool) {
	key := broadcasterName + broadcasterID.String()
	val, err := s.client.Get(ctx, key).Result()
	if err != nil {
		return "", false
	}
	return val, true
}

// ── In-app pub/sub (fan-out to connected WebSocket clients) ──────────────────

// PublishInApp publishes a notification payload to the user's in-app Redis channel.
// WebSocket gateways subscribe to these channels and forward to connected clients.
func (s *Store) PublishInApp(ctx context.Context, userID uuid.UUID, payload string) error {
	channel := fmt.Sprintf("notif:inapp:%s", userID.String())
	return s.client.Publish(ctx, channel, payload).Err()
}
