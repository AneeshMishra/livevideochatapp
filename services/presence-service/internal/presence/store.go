package presence

import (
	"context"
	"fmt"
	"strconv"
	"time"

	"github.com/redis/go-redis/v9"
)

// Redis key schema:
//   presence:user:{userId}           STRING  roomId (TTL = heartbeatTTL)
//   presence:room:{roomId}:viewers   ZSET    member=userId, score=unix-ms (heartbeat timestamp)
//   presence:active:rooms            ZSET    member=roomId, score=unix-ms (last activity)

func userKey(userID string) string     { return "presence:user:" + userID }
func viewersKey(roomID string) string  { return "presence:room:" + roomID + ":viewers" }

const activeRoomsKey = "presence:active:rooms"

type Store struct {
	rdb          *redis.Client
	heartbeatTTL time.Duration
}

func NewStore(rdb *redis.Client, heartbeatTTLSec int) *Store {
	return &Store{
		rdb:          rdb,
		heartbeatTTL: time.Duration(heartbeatTTLSec) * time.Second,
	}
}

// Heartbeat registers or refreshes a user's presence in a room.
// Returns (isNewJoin, previousRoomID, error).
// isNewJoin is true when this is the first heartbeat or the user switched rooms.
func (s *Store) Heartbeat(ctx context.Context, userID, roomID string) (isNewJoin bool, prevRoomID string, err error) {
	now := float64(time.Now().UnixMilli())

	// Read current room before updating (pipeline can't read and write atomically here).
	prevRoomID, _ = s.rdb.Get(ctx, userKey(userID)).Result()
	isNewJoin = prevRoomID == "" || prevRoomID != roomID

	pipe := s.rdb.Pipeline()
	// Refresh user→room mapping with TTL.
	pipe.Set(ctx, userKey(userID), roomID, s.heartbeatTTL)
	// Update user's score in the room viewer set.
	pipe.ZAdd(ctx, viewersKey(roomID), redis.Z{Score: now, Member: userID})
	// Room viewer set TTL: 2× heartbeat window so a cold read still works.
	pipe.Expire(ctx, viewersKey(roomID), s.heartbeatTTL*4)
	// Track room as active for the sweeper.
	pipe.ZAdd(ctx, activeRoomsKey, redis.Z{Score: now, Member: roomID})

	if _, err = pipe.Exec(ctx); err != nil {
		return false, "", fmt.Errorf("heartbeat pipeline: %w", err)
	}

	// If the user switched rooms, remove them from the old room's viewer set.
	if isNewJoin && prevRoomID != "" && prevRoomID != roomID {
		s.rdb.ZRem(ctx, viewersKey(prevRoomID), userID)
	}

	return isNewJoin, prevRoomID, nil
}

// Leave removes a user from presence entirely.
// Returns the room they were in, or "" if they were not tracked.
func (s *Store) Leave(ctx context.Context, userID string) (roomID string, err error) {
	roomID, err = s.rdb.Get(ctx, userKey(userID)).Result()
	if err == redis.Nil {
		return "", nil // already expired
	}
	if err != nil {
		return "", err
	}

	pipe := s.rdb.Pipeline()
	pipe.Del(ctx, userKey(userID))
	pipe.ZRem(ctx, viewersKey(roomID), userID)
	_, err = pipe.Exec(ctx)
	return roomID, err
}

// RoomViewerCount returns the count of users with a recent heartbeat in a room.
func (s *Store) RoomViewerCount(ctx context.Context, roomID string) (int64, error) {
	cutoff := s.staleCutoff()
	key := viewersKey(roomID)

	pipe := s.rdb.Pipeline()
	pipe.ZRemRangeByScore(ctx, key, "0", cutoff)
	cardCmd := pipe.ZCard(ctx, key)

	if _, err := pipe.Exec(ctx); err != nil {
		return 0, err
	}
	return cardCmd.Val(), nil
}

// RoomViewerCounts returns viewer counts for a batch of rooms in a single pipeline.
func (s *Store) RoomViewerCounts(ctx context.Context, roomIDs []string) (map[string]int64, error) {
	cutoff := s.staleCutoff()
	pipe := s.rdb.Pipeline()

	// First pass: remove stale entries in all rooms.
	for _, rid := range roomIDs {
		pipe.ZRemRangeByScore(ctx, viewersKey(rid), "0", cutoff)
	}
	// Second pass: ZCARD for all rooms.
	cardCmds := make([]*redis.IntCmd, len(roomIDs))
	for i, rid := range roomIDs {
		cardCmds[i] = pipe.ZCard(ctx, viewersKey(rid))
	}
	if _, err := pipe.Exec(ctx); err != nil {
		return nil, err
	}

	counts := make(map[string]int64, len(roomIDs))
	for i, rid := range roomIDs {
		counts[rid] = cardCmds[i].Val()
	}
	return counts, nil
}

// UserPresence returns the room a user is currently in, and whether they are online.
func (s *Store) UserPresence(ctx context.Context, userID string) (roomID string, online bool, err error) {
	val, err := s.rdb.Get(ctx, userKey(userID)).Result()
	if err == redis.Nil {
		return "", false, nil
	}
	if err != nil {
		return "", false, err
	}
	return val, true, nil
}

// ActiveRooms returns rooms that have had activity within the given duration.
func (s *Store) ActiveRooms(ctx context.Context, since time.Duration) ([]string, error) {
	cutoff := strconv.FormatInt(time.Now().Add(-since).UnixMilli(), 10)
	return s.rdb.ZRangeByScore(ctx, activeRoomsKey, &redis.ZRangeBy{
		Min: cutoff,
		Max: "+inf",
	}).Result()
}

func (s *Store) Ping(ctx context.Context) error {
	return s.rdb.Ping(ctx).Err()
}

// staleCutoff returns the ZRANGEBYSCORE max value for entries older than heartbeatTTL.
func (s *Store) staleCutoff() string {
	return strconv.FormatInt(time.Now().Add(-s.heartbeatTTL).UnixMilli(), 10)
}
