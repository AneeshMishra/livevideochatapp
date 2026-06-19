package ws

import (
	"context"
	"encoding/json"
	"strings"
	"sync"
	"time"
	"unicode/utf8"

	"github.com/google/uuid"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"

	"github.com/platform/chat-service/internal/message"
	"github.com/platform/chat-service/internal/pubsub"
	"github.com/platform/chat-service/internal/ratelimit"
	"github.com/platform/chat-service/internal/store"
)

const maxContentRunes = 500

// Hub manages all active rooms and coordinates message fan-out.
// It also implements the kafka.Broadcaster interface (BroadcastToRoom).
type Hub struct {
	mu          sync.RWMutex
	rooms       map[string]*Room
	pubsub      *pubsub.Client
	store       *store.ScyllaStore
	rateLimiter *ratelimit.Limiter
	logger      zerolog.Logger
}

func NewHub(ps *pubsub.Client, s *store.ScyllaStore, rl *ratelimit.Limiter) *Hub {
	return &Hub{
		rooms:       make(map[string]*Room),
		pubsub:      ps,
		store:       s,
		rateLimiter: rl,
		logger:      log.With().Str("component", "hub").Logger(),
	}
}

// Run subscribes to the Redis pub/sub pattern for cross-node fan-out.
// Blocks until ctx is cancelled.
func (h *Hub) Run(ctx context.Context) error {
	h.logger.Info().Msg("hub started")
	return h.pubsub.Subscribe(ctx, h.onPubSubMessage)
}

// Register adds a client to its room. Called from the HTTP handler after WebSocket upgrade.
func (h *Hub) Register(c *Client) {
	h.mu.Lock()
	room, ok := h.rooms[c.roomID]
	if !ok {
		room = newRoom(c.roomID)
		h.rooms[c.roomID] = room
	}
	h.mu.Unlock()
	room.add(c)
	h.logger.Info().
		Str("room_id", c.roomID).
		Str("user_id", c.userID.String()).
		Msg("client joined")
}

// unregister removes a client from its room. Called by the client's ReadPump on disconnect.
func (h *Hub) unregister(c *Client) {
	h.mu.Lock()
	room, ok := h.rooms[c.roomID]
	h.mu.Unlock()
	if !ok {
		return
	}
	room.remove(c)
	h.logger.Info().
		Str("room_id", c.roomID).
		Str("user_id", c.userID.String()).
		Msg("client left")
	// Clean up empty rooms to reclaim memory.
	if room.size() == 0 {
		h.mu.Lock()
		if h.rooms[c.roomID] == room {
			delete(h.rooms, c.roomID)
		}
		h.mu.Unlock()
	}
}

// BroadcastToRoom delivers msg directly to all local clients in roomID.
// Satisfies the kafka.Broadcaster interface — called by the Kafka consumer
// when tip events arrive and need to reach viewers without going through Redis.
func (h *Hub) BroadcastToRoom(roomID string, msg []byte) {
	h.mu.RLock()
	room, ok := h.rooms[roomID]
	h.mu.RUnlock()
	if ok {
		room.broadcast(msg)
	}
}

// onPubSubMessage is called by the Redis subscriber for every message on
// chat:room:* — enables fan-out across multiple chat-service instances.
func (h *Hub) onPubSubMessage(roomID string, data []byte) {
	h.BroadcastToRoom(roomID, data)
}

// processInbound handles a raw WebSocket frame from a client's ReadPump.
func (h *Hub) processInbound(c *Client, raw []byte) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	var in message.InboundMessage
	if err := json.Unmarshal(raw, &in); err != nil {
		h.sendError(c, "invalid JSON")
		return
	}

	switch in.Type {
	case message.TypeInboundChat:
		h.handleChat(ctx, c, in)
	case message.TypePing:
		h.sendTo(c, &message.OutboundMessage{
			Type:      message.TypePong,
			RoomID:    c.roomID,
			Timestamp: time.Now().UTC(),
		})
	default:
		h.sendError(c, "unknown message type: "+in.Type)
	}
}

func (h *Hub) handleChat(ctx context.Context, c *Client, in message.InboundMessage) {
	content := sanitize(in.Content)
	if content == "" {
		h.sendError(c, "content must not be empty")
		return
	}

	allowed, err := h.rateLimiter.Allow(ctx, c.userID.String(), c.roomID)
	if err != nil {
		h.logger.Error().Err(err).Str("user_id", c.userID.String()).Msg("rate limiter error")
	}
	if !allowed {
		h.sendError(c, "rate limit exceeded — please slow down")
		return
	}

	msgID := uuid.New()
	now := time.Now().UTC()

	out := &message.OutboundMessage{
		Type:      message.TypeChatMessage,
		MessageID: msgID.String(),
		RoomID:    c.roomID,
		UserID:    c.userID.String(),
		Username:  c.username,
		Content:   content,
		Timestamp: now,
	}

	rec := &message.ChatRecord{
		RoomID:     uuid.MustParse(c.roomID),
		DateBucket: now.Format("2006-01-02"),
		CreatedAt:  now,
		MessageID:  msgID,
		UserID:     c.userID,
		Username:   c.username,
		Content:    content,
		MsgType:    "CHAT",
	}
	if err := h.store.SaveMessage(ctx, rec); err != nil {
		h.logger.Error().Err(err).
			Str("room_id", c.roomID).
			Str("user_id", c.userID.String()).
			Msg("ScyllaDB write failed")
		h.sendError(c, "message could not be saved; try again")
		return
	}

	data, _ := json.Marshal(out)

	// Publish to Redis so all chat-service nodes receive and fan out.
	if err := h.pubsub.Publish(ctx, c.roomID, data); err != nil {
		h.logger.Error().Err(err).Str("room_id", c.roomID).Msg("Redis publish failed; falling back to local broadcast")
		h.BroadcastToRoom(c.roomID, data)
	}
	// The Redis subscriber will call BroadcastToRoom on this node too,
	// so we do NOT call it again here when Publish succeeds.
}

func (h *Hub) sendTo(c *Client, out *message.OutboundMessage) {
	data, _ := json.Marshal(out)
	c.trySend(data)
}

func (h *Hub) sendError(c *Client, errMsg string) {
	h.sendTo(c, &message.OutboundMessage{
		Type:      message.TypeError,
		RoomID:    c.roomID,
		Content:   errMsg,
		Timestamp: time.Now().UTC(),
	})
}

// sanitize trims whitespace and caps content at maxContentRunes Unicode code points.
func sanitize(s string) string {
	s = strings.TrimSpace(s)
	if utf8.RuneCountInString(s) > maxContentRunes {
		// Truncate safely at a rune boundary.
		i := 0
		for n := 0; n < maxContentRunes; n++ {
			_, size := utf8.DecodeRuneInString(s[i:])
			i += size
		}
		s = s[:i]
	}
	return s
}
