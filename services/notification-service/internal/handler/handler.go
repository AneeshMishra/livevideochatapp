package handler

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"

	"github.com/google/uuid"
	"github.com/rs/zerolog/log"

	"github.com/platform/notification-service/internal/auth"
	"github.com/platform/notification-service/internal/db"
	"github.com/platform/notification-service/internal/model"
)

type Handler struct {
	db        *db.Postgres
	validator *auth.Validator
}

func New(database *db.Postgres, validator *auth.Validator) *Handler {
	return &Handler{db: database, validator: validator}
}

func (h *Handler) Routes() http.Handler {
	mux := http.NewServeMux()

	// Notification history
	mux.HandleFunc("GET /api/v1/notifications/history", h.history)

	// Preferences
	mux.HandleFunc("GET /api/v1/notifications/preferences", h.getPreferences)
	mux.HandleFunc("PUT /api/v1/notifications/preferences", h.updatePreferences)

	// Device tokens (for FCM/APNs push)
	mux.HandleFunc("POST /api/v1/notifications/device-tokens", h.registerDeviceToken)
	mux.HandleFunc("DELETE /api/v1/notifications/device-tokens/{token}", h.deregisterDeviceToken)

	mux.HandleFunc("GET /health", h.health)
	mux.HandleFunc("GET /ready", h.ready)

	return mux
}

// GET /api/v1/notifications/history?page=0&size=20
func (h *Handler) history(w http.ResponseWriter, r *http.Request) {
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}
	limit, offset := pagination(r, 20, 100)
	logs, err := h.db.ListLogByUser(r.Context(), claims.UserID, limit, offset)
	if err != nil {
		log.Error().Err(err).Str("user_id", claims.UserID.String()).Msg("notification history")
		jsonErr(w, "internal error", http.StatusInternalServerError)
		return
	}
	type logResp struct {
		ID        uuid.UUID `json:"id"`
		EventType string    `json:"eventType"`
		Channel   string    `json:"channel"`
		Status    string    `json:"status"`
		Title     string    `json:"title"`
		Body      string    `json:"body"`
		CreatedAt string    `json:"createdAt"`
	}
	items := make([]logResp, len(logs))
	for i, l := range logs {
		items[i] = logResp{
			ID:        l.ID,
			EventType: l.EventType,
			Channel:   l.Channel,
			Status:    l.Status,
			Title:     l.Title,
			Body:      l.Body,
			CreatedAt: l.CreatedAt.String(),
		}
	}
	jsonOK(w, map[string]any{"items": items, "count": len(items)})
}

// GET /api/v1/notifications/preferences
func (h *Handler) getPreferences(w http.ResponseWriter, r *http.Request) {
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}
	prefs, err := h.db.GetPreferences(r.Context(), claims.UserID)
	if err != nil {
		jsonErr(w, "internal error", http.StatusInternalServerError)
		return
	}
	jsonOK(w, map[string]any{"preferences": prefs})
}

// PUT /api/v1/notifications/preferences
// Body: [{"eventType":"TIP_RECEIVED","channel":"PUSH","enabled":false}, ...]
func (h *Handler) updatePreferences(w http.ResponseWriter, r *http.Request) {
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}
	var updates []struct {
		EventType string `json:"eventType"`
		Channel   string `json:"channel"`
		Enabled   bool   `json:"enabled"`
	}
	if err := json.NewDecoder(r.Body).Decode(&updates); err != nil {
		jsonErr(w, "invalid request body", http.StatusBadRequest)
		return
	}

	validChannels := map[string]bool{
		model.ChannelEmail: true, model.ChannelPush: true,
		model.ChannelInApp: true, model.ChannelWhatsApp: true,
	}

	for _, u := range updates {
		if !validChannels[u.Channel] {
			jsonErr(w, "invalid channel: "+u.Channel, http.StatusBadRequest)
			return
		}
		pref := &model.UserPreference{
			ID:        uuid.New(),
			UserID:    claims.UserID,
			EventType: u.EventType,
			Channel:   u.Channel,
			Enabled:   u.Enabled,
		}
		if err := h.db.UpsertPreference(r.Context(), pref); err != nil {
			log.Error().Err(err).Str("user_id", claims.UserID.String()).Msg("upsert preference")
			jsonErr(w, "internal error", http.StatusInternalServerError)
			return
		}
	}
	jsonOK(w, map[string]bool{"ok": true})
}

// POST /api/v1/notifications/device-tokens
// Body: {"token":"fcm-token-string","platform":"FCM"}
func (h *Handler) registerDeviceToken(w http.ResponseWriter, r *http.Request) {
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}
	var body struct {
		Token    string `json:"token"`
		Platform string `json:"platform"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.Token == "" {
		jsonErr(w, "token and platform required", http.StatusBadRequest)
		return
	}
	if body.Platform != "FCM" && body.Platform != "APNS" {
		jsonErr(w, "platform must be FCM or APNS", http.StatusBadRequest)
		return
	}
	dt := &model.DeviceToken{
		ID:       uuid.New(),
		UserID:   claims.UserID,
		Token:    body.Token,
		Platform: body.Platform,
		Active:   true,
	}
	if err := h.db.UpsertDeviceToken(r.Context(), dt); err != nil {
		log.Error().Err(err).Str("user_id", claims.UserID.String()).Msg("register device token")
		jsonErr(w, "internal error", http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
	jsonOK(w, map[string]bool{"ok": true})
}

// DELETE /api/v1/notifications/device-tokens/{token}
func (h *Handler) deregisterDeviceToken(w http.ResponseWriter, r *http.Request) {
	_, ok := h.requireAuth(w, r)
	if !ok {
		return
	}
	token := r.PathValue("token")
	if token == "" {
		jsonErr(w, "token required", http.StatusBadRequest)
		return
	}
	if err := h.db.DeactivateDeviceToken(r.Context(), token); err != nil {
		log.Error().Err(err).Msg("deregister device token")
		jsonErr(w, "internal error", http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h *Handler) health(w http.ResponseWriter, _ *http.Request) {
	jsonOK(w, map[string]string{"status": "UP"})
}

func (h *Handler) ready(w http.ResponseWriter, r *http.Request) {
	if err := h.db.Ping(r.Context()); err != nil {
		http.Error(w, `{"status":"DOWN"}`, http.StatusServiceUnavailable)
		return
	}
	jsonOK(w, map[string]string{"status": "READY"})
}

// ── helpers ───────────────────────────────────────────────────────────────────

func (h *Handler) requireAuth(w http.ResponseWriter, r *http.Request) (*auth.Claims, bool) {
	tok := r.Header.Get("Authorization")
	if strings.HasPrefix(tok, "Bearer ") {
		tok = tok[7:]
	}
	if tok == "" {
		jsonErr(w, "missing auth token", http.StatusUnauthorized)
		return nil, false
	}
	claims, err := h.validator.Validate(tok)
	if err != nil {
		jsonErr(w, "invalid token", http.StatusUnauthorized)
		return nil, false
	}
	return claims, true
}

func jsonOK(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}

func jsonErr(w http.ResponseWriter, msg string, code int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(map[string]string{"error": msg})
}

func pagination(r *http.Request, defaultSize, maxSize int) (limit, offset int) {
	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	size, _ := strconv.Atoi(r.URL.Query().Get("size"))
	if size <= 0 || size > maxSize {
		size = defaultSize
	}
	if page < 0 {
		page = 0
	}
	return size, page * size
}
