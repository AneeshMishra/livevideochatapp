package handler

import (
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"github.com/google/uuid"
	"github.com/rs/zerolog/log"

	"github.com/platform/streaming-orchestration-service/internal/auth"
	"github.com/platform/streaming-orchestration-service/internal/domain"
	"github.com/platform/streaming-orchestration-service/internal/service"
)

type Handler struct {
	orchestrator *service.Orchestrator
	validator    *auth.Validator
}

func New(o *service.Orchestrator, v *auth.Validator) *Handler {
	return &Handler{orchestrator: o, validator: v}
}

// Routes registers all HTTP routes.
func (h *Handler) Routes() http.Handler {
	mux := http.NewServeMux()

	// Broadcaster-owned operations (JWT required, must be the room's broadcaster)
	mux.HandleFunc("POST /api/v1/rooms", h.createRoom)
	mux.HandleFunc("GET /api/v1/rooms", h.listRooms)
	mux.HandleFunc("GET /api/v1/rooms/{roomId}", h.getRoom)
	mux.HandleFunc("PATCH /api/v1/rooms/{roomId}", h.updateRoom)
	mux.HandleFunc("POST /api/v1/rooms/{roomId}/start", h.startStream)
	mux.HandleFunc("POST /api/v1/rooms/{roomId}/stop", h.stopStream)

	// Viewer-facing — public (no JWT); Redis-cached hot path
	mux.HandleFunc("GET /api/v1/rooms/{roomId}/playback", h.getPlayback)

	mux.HandleFunc("GET /health", h.health)
	mux.HandleFunc("GET /ready", h.ready)

	return mux
}

// ── Broadcaster endpoints ─────────────────────────────────────────────────────

// createRoom — POST /api/v1/rooms
// Body: {"title":"My Stream","promotionThreshold":1000}
func (h *Handler) createRoom(w http.ResponseWriter, r *http.Request) {
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}

	var body struct {
		Title              string `json:"title"`
		PromotionThreshold int64  `json:"promotionThreshold"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid JSON body", http.StatusBadRequest)
		return
	}

	room, err := h.orchestrator.CreateRoom(r.Context(), claims.UserID, body.Title, body.PromotionThreshold)
	if err != nil {
		log.Error().Err(err).Str("broadcaster_id", claims.UserID.String()).Msg("createRoom failed")
		http.Error(w, "could not provision streaming channel", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(roomResponse(room))
}

// listRooms — GET /api/v1/rooms (broadcaster's own rooms)
func (h *Handler) listRooms(w http.ResponseWriter, r *http.Request) {
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}

	rooms, err := h.orchestrator.ListRooms(r.Context(), claims.UserID)
	if err != nil {
		log.Error().Err(err).Msg("listRooms failed")
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}

	resp := make([]map[string]any, 0, len(rooms))
	for _, room := range rooms {
		resp = append(resp, roomResponse(room))
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]any{"rooms": resp, "count": len(resp)})
}

// getRoom — GET /api/v1/rooms/{roomId}
func (h *Handler) getRoom(w http.ResponseWriter, r *http.Request) {
	roomID, ok := h.parseRoomID(w, r)
	if !ok {
		return
	}

	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}

	room, err := h.orchestrator.GetRoom(r.Context(), roomID)
	if err != nil {
		h.handleDomainErr(w, err)
		return
	}
	if room.BroadcasterID != claims.UserID && !claims.HasRole("ADMIN") {
		http.Error(w, "forbidden", http.StatusForbidden)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(roomResponse(room))
}

// updateRoom — PATCH /api/v1/rooms/{roomId}
// Body: {"title":"New Title"}
func (h *Handler) updateRoom(w http.ResponseWriter, r *http.Request) {
	roomID, ok := h.parseRoomID(w, r)
	if !ok {
		return
	}
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}

	var body struct {
		Title string `json:"title"`
	}
	json.NewDecoder(r.Body).Decode(&body)

	if err := h.orchestrator.UpdateTitle(r.Context(), roomID, claims.UserID, body.Title); err != nil {
		h.handleDomainErr(w, err)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]bool{"ok": true})
}

// startStream — POST /api/v1/rooms/{roomId}/start
func (h *Handler) startStream(w http.ResponseWriter, r *http.Request) {
	roomID, ok := h.parseRoomID(w, r)
	if !ok {
		return
	}
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}

	room, err := h.orchestrator.StartStream(r.Context(), roomID, claims.UserID)
	if err != nil {
		h.handleDomainErr(w, err)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]any{
		"roomId":         room.ID,
		"status":         room.Status,
		"ingestEndpoint": room.IngestEndpoint,
		"streamKey":      room.StreamKey,
		"hlsPlaybackUrl": room.HLSPlaybackURL,
	})
}

// stopStream — POST /api/v1/rooms/{roomId}/stop
func (h *Handler) stopStream(w http.ResponseWriter, r *http.Request) {
	roomID, ok := h.parseRoomID(w, r)
	if !ok {
		return
	}
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}

	room, err := h.orchestrator.StopStream(r.Context(), roomID, claims.UserID)
	if err != nil {
		h.handleDomainErr(w, err)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]any{
		"roomId":          room.ID,
		"status":          room.Status,
		"peakViewerCount": room.PeakViewerCount,
	})
}

// ── Viewer endpoint (public, Redis-cached) ────────────────────────────────────

// getPlayback — GET /api/v1/rooms/{roomId}/playback
// Returns the correct playback URL based on current delivery mode.
func (h *Handler) getPlayback(w http.ResponseWriter, r *http.Request) {
	roomID, ok := h.parseRoomID(w, r)
	if !ok {
		return
	}

	info, err := h.orchestrator.GetPlayback(r.Context(), roomID)
	if err != nil {
		h.handleDomainErr(w, err)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(info)
}

// ── Infrastructure ─────────────────────────────────────────────────────────────

func (h *Handler) health(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "UP"})
}

func (h *Handler) ready(w http.ResponseWriter, r *http.Request) {
	if err := h.orchestrator.HealthCheck(r.Context()); err != nil {
		log.Warn().Err(err).Msg("readiness check failed")
		http.Error(w, `{"status":"DOWN"}`, http.StatusServiceUnavailable)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "READY"})
}

// ── Helpers ───────────────────────────────────────────────────────────────────

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

func (h *Handler) parseRoomID(w http.ResponseWriter, r *http.Request) (uuid.UUID, bool) {
	id, err := uuid.Parse(r.PathValue("roomId"))
	if err != nil {
		http.Error(w, "invalid roomId", http.StatusBadRequest)
		return uuid.Nil, false
	}
	return id, true
}

func (h *Handler) handleDomainErr(w http.ResponseWriter, err error) {
	var notFound domain.ErrRoomNotFound
	var notAuth domain.ErrNotAuthorized
	var alreadyLive domain.ErrRoomAlreadyLive

	switch {
	case errors.As(err, &notFound):
		http.Error(w, err.Error(), http.StatusNotFound)
	case errors.As(err, &notAuth):
		http.Error(w, err.Error(), http.StatusForbidden)
	case errors.As(err, &alreadyLive):
		http.Error(w, err.Error(), http.StatusConflict)
	default:
		log.Error().Err(err).Msg("internal error")
		http.Error(w, "internal error", http.StatusInternalServerError)
	}
}

func roomResponse(r *domain.Room) map[string]any {
	return map[string]any{
		"id":                 r.ID,
		"broadcasterId":      r.BroadcasterID,
		"title":              r.Title,
		"status":             r.Status,
		"deliveryMode":       r.DeliveryMode,
		"ingestEndpoint":     r.IngestEndpoint,
		"hlsPlaybackUrl":     r.HLSPlaybackURL,
		"viewerCount":        r.ViewerCount,
		"peakViewerCount":    r.PeakViewerCount,
		"promotionThreshold": r.PromotionThreshold,
		"provider":           r.Provider,
		"createdAt":          r.CreatedAt,
		"updatedAt":          r.UpdatedAt,
	}
}
