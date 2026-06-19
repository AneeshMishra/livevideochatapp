package handler

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"github.com/rs/zerolog/log"

	"github.com/platform/chat-service/internal/auth"
	"github.com/platform/chat-service/internal/config"
	"github.com/platform/chat-service/internal/message"
	"github.com/platform/chat-service/internal/store"
	"github.com/platform/chat-service/internal/ws"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	// TODO: restrict CheckOrigin to known domains in production.
	CheckOrigin: func(r *http.Request) bool { return true },
}

type Handler struct {
	hub       *ws.Hub
	store     *store.ScyllaStore
	validator *auth.Validator
	cfg       *config.Config
}

func New(hub *ws.Hub, s *store.ScyllaStore, v *auth.Validator, cfg *config.Config) *Handler {
	return &Handler{hub: hub, store: s, validator: v, cfg: cfg}
}

// Routes returns the ServeMux for the chat service.
// Requires Go 1.22+ for method+path routing and r.PathValue.
func (h *Handler) Routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /ws/room/{roomId}", h.serveWS)
	mux.HandleFunc("GET /api/v1/chat/rooms/{roomId}/history", h.getHistory)
	mux.HandleFunc("GET /health", h.health)
	mux.HandleFunc("GET /ready", h.ready)
	return mux
}

// serveWS upgrades an HTTP request to a WebSocket connection for a chat room.
// JWT is accepted via Authorization: Bearer <token> header or ?token= query parameter
// (query param is used by browser WebSocket API which cannot set headers).
func (h *Handler) serveWS(w http.ResponseWriter, r *http.Request) {
	roomID := r.PathValue("roomId")
	if _, err := uuid.Parse(roomID); err != nil {
		http.Error(w, "invalid roomId", http.StatusBadRequest)
		return
	}

	tokenStr := extractToken(r)
	if tokenStr == "" {
		http.Error(w, "missing auth token", http.StatusUnauthorized)
		return
	}
	claims, err := h.validator.Validate(tokenStr)
	if err != nil {
		http.Error(w, "invalid token", http.StatusUnauthorized)
		return
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Error().Err(err).Str("room_id", roomID).Msg("websocket upgrade failed")
		return
	}

	clientLog := log.With().
		Str("room_id", roomID).
		Str("user_id", claims.UserID.String()).
		Str("username", claims.Username).
		Logger()

	c := ws.NewClient(conn, claims.UserID, claims.Username, claims.Roles, roomID, h.hub, clientLog)
	h.hub.Register(c)
	c.SendWelcome()

	go c.WritePump()
	go c.ReadPump(h.cfg.MaxMessageBytes)
}

// getHistory returns recent chat messages for a room from ScyllaDB.
func (h *Handler) getHistory(w http.ResponseWriter, r *http.Request) {
	roomIDStr := r.PathValue("roomId")
	roomID, err := uuid.Parse(roomIDStr)
	if err != nil {
		http.Error(w, "invalid roomId", http.StatusBadRequest)
		return
	}

	limit := 50
	if l := r.URL.Query().Get("limit"); l != "" {
		if n, err := strconv.Atoi(l); err == nil && n > 0 && n <= 200 {
			limit = n
		}
	}

	// Cursor-based pagination: ?before=<RFC3339 timestamp>
	var (
		records []*message.ChatRecord
		fetchErr error
	)
	if beforeStr := r.URL.Query().Get("before"); beforeStr != "" {
		before, err := time.Parse(time.RFC3339Nano, beforeStr)
		if err != nil {
			http.Error(w, "invalid 'before' timestamp", http.StatusBadRequest)
			return
		}
		records, fetchErr = h.store.GetHistoryBefore(r.Context(), roomID, before, limit)
	} else {
		records, fetchErr = h.store.GetHistory(r.Context(), roomID, limit)
	}

	if fetchErr != nil {
		log.Error().Err(fetchErr).Str("room_id", roomIDStr).Msg("fetch history")
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}

	msgs := make([]message.OutboundMessage, 0, len(records))
	for _, rec := range records {
		msgs = append(msgs, message.OutboundMessage{
			Type:      message.TypeChatMessage,
			MessageID: rec.MessageID.String(),
			RoomID:    rec.RoomID.String(),
			UserID:    rec.UserID.String(),
			Username:  rec.Username,
			Content:   rec.Content,
			Timestamp: rec.CreatedAt,
		})
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]any{
		"messages": msgs,
		"count":    len(msgs),
	})
}

func (h *Handler) health(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "UP"})
}

func (h *Handler) ready(w http.ResponseWriter, r *http.Request) {
	if err := h.store.Ping(r.Context()); err != nil {
		log.Warn().Err(err).Msg("readiness: ScyllaDB not ready")
		http.Error(w, `{"status":"DOWN","reason":"ScyllaDB not ready"}`, http.StatusServiceUnavailable)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "READY"})
}

// extractToken reads the JWT from Authorization header or ?token= query param.
func extractToken(r *http.Request) string {
	if hdr := r.Header.Get("Authorization"); hdr != "" {
		if strings.HasPrefix(hdr, "Bearer ") {
			return hdr[7:]
		}
	}
	return r.URL.Query().Get("token")
}
