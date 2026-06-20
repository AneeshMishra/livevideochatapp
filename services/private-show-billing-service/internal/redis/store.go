package redis

import (
	"context"
	"fmt"
	"strconv"
	"time"

	"github.com/google/uuid"
	rdb "github.com/redis/go-redis/v9"

	"github.com/platform/private-show-billing-service/internal/model"
)

const (
	activeSessionsKey  = "pshow:active_sessions"
	sessionKeyPrefix   = "pshow:session:"
	viewerActivePrefix = "pshow:viewer:"
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

// CreateSession stores the active session in Redis.
// viewerActivePrefix ensures a viewer can only have one active session.
func (s *Store) CreateSession(ctx context.Context, sess *model.Session) error {
	sessionKey := sessionKeyPrefix + sess.ID.String()
	viewerKey := viewerActivePrefix + sess.ViewerID.String() + ":active"

	pipe := s.client.TxPipeline()
	pipe.HSet(ctx, sessionKey,
		"viewer_id", sess.ViewerID.String(),
		"broadcaster_id", sess.BroadcasterID.String(),
		"room_id", sess.RoomID.String(),
		"show_type", string(sess.ShowType),
		"rate_per_minute", strconv.FormatInt(sess.RatePerMinute, 10),
		"started_at", strconv.FormatInt(sess.StartedAt.Unix(), 10),
		"billed_minutes", "0",
		"status", string(model.StatusActive),
	)
	pipe.SAdd(ctx, activeSessionsKey, sess.ID.String())
	pipe.Set(ctx, viewerKey, sess.ID.String(), 0)
	_, err := pipe.Exec(ctx)
	return err
}

// GetSession retrieves session state from Redis. Returns nil, nil if not found.
func (s *Store) GetSession(ctx context.Context, sessionID uuid.UUID) (*RedisSession, error) {
	key := sessionKeyPrefix + sessionID.String()
	vals, err := s.client.HGetAll(ctx, key).Result()
	if err != nil {
		return nil, err
	}
	if len(vals) == 0 {
		return nil, nil
	}
	return parseRedisSession(sessionID, vals)
}

// ActiveSessionIDs returns all session IDs currently in the active set.
func (s *Store) ActiveSessionIDs(ctx context.Context) ([]string, error) {
	return s.client.SMembers(ctx, activeSessionsKey).Result()
}

// IncrBilledMinutes atomically increments the billed_minutes counter and returns the new value.
func (s *Store) IncrBilledMinutes(ctx context.Context, sessionID uuid.UUID) (int64, error) {
	key := sessionKeyPrefix + sessionID.String()
	return s.client.HIncrBy(ctx, key, "billed_minutes", 1).Result()
}

// SetStatus updates the status field of a session.
func (s *Store) SetStatus(ctx context.Context, sessionID uuid.UUID, status model.SessionStatus) error {
	key := sessionKeyPrefix + sessionID.String()
	return s.client.HSet(ctx, key, "status", string(status)).Err()
}

// DeleteSession removes the session from Redis (called after persisting COMPLETED state).
func (s *Store) DeleteSession(ctx context.Context, sess *RedisSession) error {
	sessionKey := sessionKeyPrefix + sess.SessionID.String()
	viewerKey := viewerActivePrefix + sess.ViewerID.String() + ":active"

	pipe := s.client.TxPipeline()
	pipe.Del(ctx, sessionKey)
	pipe.SRem(ctx, activeSessionsKey, sess.SessionID.String())
	pipe.Del(ctx, viewerKey)
	_, err := pipe.Exec(ctx)
	return err
}

// GetViewerActiveSession returns the active session ID for a viewer, or "" if none.
func (s *Store) GetViewerActiveSession(ctx context.Context, viewerID uuid.UUID) (string, error) {
	key := viewerActivePrefix + viewerID.String() + ":active"
	val, err := s.client.Get(ctx, key).Result()
	if err == rdb.Nil {
		return "", nil
	}
	return val, err
}

// RedisSession holds the denormalised session state read from Redis hashes.
type RedisSession struct {
	SessionID     uuid.UUID
	ViewerID      uuid.UUID
	BroadcasterID uuid.UUID
	RoomID        uuid.UUID
	ShowType      model.ShowType
	RatePerMinute int64
	StartedAt     time.Time
	BilledMinutes int
	Status        model.SessionStatus
}

func parseRedisSession(sessionID uuid.UUID, vals map[string]string) (*RedisSession, error) {
	parse := func(key string) (uuid.UUID, error) {
		return uuid.Parse(vals[key])
	}

	viewerID, err := parse("viewer_id")
	if err != nil {
		return nil, fmt.Errorf("parse viewer_id: %w", err)
	}
	broadcasterID, err := parse("broadcaster_id")
	if err != nil {
		return nil, fmt.Errorf("parse broadcaster_id: %w", err)
	}
	roomID, err := parse("room_id")
	if err != nil {
		return nil, fmt.Errorf("parse room_id: %w", err)
	}

	rate, _ := strconv.ParseInt(vals["rate_per_minute"], 10, 64)
	startedUnix, _ := strconv.ParseInt(vals["started_at"], 10, 64)
	billedMinutes, _ := strconv.Atoi(vals["billed_minutes"])

	return &RedisSession{
		SessionID:     sessionID,
		ViewerID:      viewerID,
		BroadcasterID: broadcasterID,
		RoomID:        roomID,
		ShowType:      model.ShowType(vals["show_type"]),
		RatePerMinute: rate,
		StartedAt:     time.Unix(startedUnix, 0),
		BilledMinutes: billedMinutes,
		Status:        model.SessionStatus(vals["status"]),
	}, nil
}
