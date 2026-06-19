package handler

import (
	"encoding/json"
	"net/http"
	"strings"

	"github.com/google/uuid"
	"github.com/rs/zerolog/log"

	"github.com/platform/presence-service/internal/auth"
	"github.com/platform/presence-service/internal/kafka"
	"github.com/platform/presence-service/internal/presence"
)

type Handler struct {
	store     *presence.Store
	producer  *kafka.Producer
	validator *auth.Validator
}

func New(store *presence.Store, producer *kafka.Producer, validator *auth.Validator) *Handler {
	return &Handler{store: store, producer: producer, validator: validator}
}

// Routes returns the mux for all presence endpoints.
func (h *Handler) Routes() http.Handler {
	mux := http.NewServeMux()

	// Authenticated — called by clients on a ~15 s interval
	mux.HandleFunc("POST /api/v1/presence/heartbeat", h.heartbeat)
	// Authenticated — called on explicit tab close / leave
	mux.HandleFunc("DELETE /api/v1/presence/leave", h.leave)

	// Public — polled by the discovery/catalog grid and by individual room views
	mux.HandleFunc("GET /api/v1/presence/rooms/{roomId}/count", h.roomCount)
	// Public — batch viewer counts for the discovery grid (?ids=uuid1,uuid2,...)
	mux.HandleFunc("GET /api/v1/presence/rooms/counts", h.roomCounts)
	// Public — is this user online, and in which room?
	mux.HandleFunc("GET /api/v1/presence/users/{userId}", h.userPresence)

	mux.HandleFunc("GET /health", h.health)
	mux.HandleFunc("GET /ready", h.ready)

	return mux
}

// heartbeat — POST /api/v1/presence/heartbeat
// Body: {"roomId":"<uuid>"}
func (h *Handler) heartbeat(w http.ResponseWriter, r *http.Request) {
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}

	var body struct {
		RoomID string `json:"roomId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.RoomID == "" {
		http.Error(w, "roomId required", http.StatusBadRequest)
		return
	}
	if _, err := uuid.Parse(body.RoomID); err != nil {
		http.Error(w, "roomId must be a valid UUID", http.StatusBadRequest)
		return
	}

	isNew, prevRoom, err := h.store.Heartbeat(r.Context(), claims.UserID.String(), body.RoomID)
	if err != nil {
		log.Error().Err(err).Str("user_id", claims.UserID.String()).Msg("heartbeat failed")
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}

	if isNew {
		h.producer.PublishUserJoined(r.Context(), claims.UserID.String(), body.RoomID)
		// Emit left event for the room the user was previously in.
		if prevRoom != "" && prevRoom != body.RoomID {
			h.producer.PublishUserLeft(r.Context(), claims.UserID.String(), prevRoom)
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]bool{"ok": true})
}

// leave — DELETE /api/v1/presence/leave
func (h *Handler) leave(w http.ResponseWriter, r *http.Request) {
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}

	roomID, err := h.store.Leave(r.Context(), claims.UserID.String())
	if err != nil {
		log.Error().Err(err).Str("user_id", claims.UserID.String()).Msg("leave failed")
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}

	if roomID != "" {
		h.producer.PublishUserLeft(r.Context(), claims.UserID.String(), roomID)
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]bool{"ok": true})
}

// roomCount — GET /api/v1/presence/rooms/{roomId}/count
func (h *Handler) roomCount(w http.ResponseWriter, r *http.Request) {
	roomID := r.PathValue("roomId")
	if _, err := uuid.Parse(roomID); err != nil {
		http.Error(w, "invalid roomId", http.StatusBadRequest)
		return
	}

	count, err := h.store.RoomViewerCount(r.Context(), roomID)
	if err != nil {
		log.Error().Err(err).Str("room_id", roomID).Msg("roomCount failed")
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]any{"roomId": roomID, "count": count})
}

// roomCounts — GET /api/v1/presence/rooms/counts?ids=uuid1,uuid2,...
// Used by the discovery/catalog grid to batch-fetch viewer counts for many rooms.
func (h *Handler) roomCounts(w http.ResponseWriter, r *http.Request) {
	idsParam := r.URL.Query().Get("ids")
	if idsParam == "" {
		http.Error(w, "ids query parameter required", http.StatusBadRequest)
		return
	}
	rawIDs := strings.Split(idsParam, ",")
	var roomIDs []string
	for _, raw := range rawIDs {
		raw = strings.TrimSpace(raw)
		if _, err := uuid.Parse(raw); err == nil {
			roomIDs = append(roomIDs, raw)
		}
	}
	if len(roomIDs) == 0 {
		http.Error(w, "no valid UUIDs in ids", http.StatusBadRequest)
		return
	}
	if len(roomIDs) > 200 {
		roomIDs = roomIDs[:200] // cap batch size
	}

	counts, err := h.store.RoomViewerCounts(r.Context(), roomIDs)
	if err != nil {
		log.Error().Err(err).Msg("roomCounts batch failed")
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]any{"counts": counts})
}

// userPresence — GET /api/v1/presence/users/{userId}
func (h *Handler) userPresence(w http.ResponseWriter, r *http.Request) {
	userIDStr := r.PathValue("userId")
	if _, err := uuid.Parse(userIDStr); err != nil {
		http.Error(w, "invalid userId", http.StatusBadRequest)
		return
	}

	roomID, online, err := h.store.UserPresence(r.Context(), userIDStr)
	if err != nil {
		log.Error().Err(err).Str("user_id", userIDStr).Msg("userPresence failed")
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}

	resp := map[string]any{"userId": userIDStr, "online": online}
	if online {
		resp["roomId"] = roomID
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}

func (h *Handler) health(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "UP"})
}

func (h *Handler) ready(w http.ResponseWriter, r *http.Request) {
	if err := h.store.Ping(r.Context()); err != nil {
		log.Warn().Err(err).Msg("readiness: Redis not ready")
		http.Error(w, `{"status":"DOWN","reason":"Redis not ready"}`, http.StatusServiceUnavailable)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "READY"})
}

// requireAuth extracts and validates the JWT from the request.
func (h *Handler) requireAuth(w http.ResponseWriter, r *http.Request) (*auth.Claims, bool) {
	tok := r.Header.Get("Authorization")
	if strings.HasPrefix(tok, "Bearer ") {
		tok = tok[7:]
	}
	if tok == "" {
		http.Error(w, "missing auth token", http.StatusUnauthorized)
		return nil, false
	}
	claims, err := h.validator.Validate(tok)
	if err != nil {
		http.Error(w, "invalid token", http.StatusUnauthorized)
		return nil, false
	}
	return claims, true
}
