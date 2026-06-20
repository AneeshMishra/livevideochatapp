package handler

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/rs/zerolog/log"

	"github.com/platform/private-show-billing-service/internal/auth"
	"github.com/platform/private-show-billing-service/internal/billing"
	"github.com/platform/private-show-billing-service/internal/db"
	"github.com/platform/private-show-billing-service/internal/model"
)

type Handler struct {
	svc       *billing.Service
	db        *db.Postgres
	validator *auth.Validator
}

func New(svc *billing.Service, database *db.Postgres, validator *auth.Validator) *Handler {
	return &Handler{svc: svc, db: database, validator: validator}
}

func (h *Handler) Routes() http.Handler {
	mux := http.NewServeMux()

	// Session lifecycle
	mux.HandleFunc("POST /api/v1/private-shows", h.startSession)
	mux.HandleFunc("GET /api/v1/private-shows/{sessionId}", h.getSession)
	mux.HandleFunc("DELETE /api/v1/private-shows/{sessionId}", h.endSession)
	mux.HandleFunc("POST /api/v1/private-shows/{sessionId}/pause", h.pauseSession)
	mux.HandleFunc("POST /api/v1/private-shows/{sessionId}/resume", h.resumeSession)

	// History
	mux.HandleFunc("GET /api/v1/private-shows/viewer/history", h.viewerHistory)
	mux.HandleFunc("GET /api/v1/private-shows/broadcaster/history", h.broadcasterHistory)

	mux.HandleFunc("GET /health", h.health)
	mux.HandleFunc("GET /ready", h.ready)

	return mux
}

// POST /api/v1/private-shows
// Body: { "broadcasterId", "roomId", "showType", "ratePerMinute" }
func (h *Handler) startSession(w http.ResponseWriter, r *http.Request) {
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}

	var body struct {
		BroadcasterID string `json:"broadcasterId"`
		RoomID        string `json:"roomId"`
		ShowType      string `json:"showType"`
		RatePerMinute int64  `json:"ratePerMinute"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		jsonErr(w, "invalid request body", http.StatusBadRequest)
		return
	}

	broadcasterID, err := uuid.Parse(body.BroadcasterID)
	if err != nil {
		jsonErr(w, "invalid broadcasterId", http.StatusBadRequest)
		return
	}
	roomID, err := uuid.Parse(body.RoomID)
	if err != nil {
		jsonErr(w, "invalid roomId", http.StatusBadRequest)
		return
	}
	showType := model.ShowType(body.ShowType)
	if showType != model.ShowTypePrivate && showType != model.ShowTypeSpy && showType != model.ShowTypeGroup {
		jsonErr(w, "showType must be PRIVATE, SPY, or GROUP", http.StatusBadRequest)
		return
	}
	if body.RatePerMinute <= 0 {
		jsonErr(w, "ratePerMinute must be positive", http.StatusBadRequest)
		return
	}

	sess, err := h.svc.StartSession(r.Context(), claims.UserID, broadcasterID, roomID, showType, body.RatePerMinute)
	if err != nil {
		if errors.Is(err, billing.ErrViewerHasSession) {
			jsonErr(w, "viewer already has an active private show", http.StatusConflict)
			return
		}
		log.Error().Err(err).Str("viewer_id", claims.UserID.String()).Msg("start session")
		jsonErr(w, "internal error", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(sessionResponse(sess))
}

// GET /api/v1/private-shows/{sessionId}
func (h *Handler) getSession(w http.ResponseWriter, r *http.Request) {
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}
	sid, err := uuid.Parse(r.PathValue("sessionId"))
	if err != nil {
		jsonErr(w, "invalid sessionId", http.StatusBadRequest)
		return
	}

	sess, err := h.svc.GetSession(r.Context(), sid)
	if err != nil {
		jsonErr(w, "session not found", http.StatusNotFound)
		return
	}
	if sess.ViewerID != claims.UserID && sess.BroadcasterID != claims.UserID && !claims.IsAdmin() {
		jsonErr(w, "forbidden", http.StatusForbidden)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(sessionResponse(sess))
}

// DELETE /api/v1/private-shows/{sessionId}
func (h *Handler) endSession(w http.ResponseWriter, r *http.Request) {
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}
	sid, err := uuid.Parse(r.PathValue("sessionId"))
	if err != nil {
		jsonErr(w, "invalid sessionId", http.StatusBadRequest)
		return
	}

	// Determine end reason from who is calling
	reason := model.EndReasonViewerEnded
	var body struct {
		Reason string `json:"reason"`
	}
	if json.NewDecoder(r.Body).Decode(&body) == nil && body.Reason == "BROADCASTER_ENDED" {
		reason = model.EndReasonBroadcasterEnded
	}

	sess, err := h.svc.EndSession(r.Context(), sid, claims.UserID, reason)
	if err != nil {
		switch {
		case errors.Is(err, billing.ErrSessionNotFound):
			jsonErr(w, "session not found", http.StatusNotFound)
		case errors.Is(err, billing.ErrNotAuthorized):
			jsonErr(w, "forbidden", http.StatusForbidden)
		default:
			log.Error().Err(err).Str("session_id", sid.String()).Msg("end session")
			jsonErr(w, "internal error", http.StatusInternalServerError)
		}
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(sessionResponse(sess))
}

// POST /api/v1/private-shows/{sessionId}/pause
func (h *Handler) pauseSession(w http.ResponseWriter, r *http.Request) {
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}
	sid, err := uuid.Parse(r.PathValue("sessionId"))
	if err != nil {
		jsonErr(w, "invalid sessionId", http.StatusBadRequest)
		return
	}
	if err := h.svc.PauseSession(r.Context(), sid, claims.UserID); err != nil {
		handleSessionErr(w, err, sid)
		return
	}
	jsonOK(w)
}

// POST /api/v1/private-shows/{sessionId}/resume
func (h *Handler) resumeSession(w http.ResponseWriter, r *http.Request) {
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}
	sid, err := uuid.Parse(r.PathValue("sessionId"))
	if err != nil {
		jsonErr(w, "invalid sessionId", http.StatusBadRequest)
		return
	}
	if err := h.svc.ResumeSession(r.Context(), sid, claims.UserID); err != nil {
		handleSessionErr(w, err, sid)
		return
	}
	jsonOK(w)
}

// GET /api/v1/private-shows/viewer/history?page=0&size=20
func (h *Handler) viewerHistory(w http.ResponseWriter, r *http.Request) {
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}
	limit, offset := pagination(r, 20, 100)
	sessions, err := h.svc.ListViewerHistory(r.Context(), claims.UserID, limit, offset)
	if err != nil {
		log.Error().Err(err).Str("viewer_id", claims.UserID.String()).Msg("viewer history")
		jsonErr(w, "internal error", http.StatusInternalServerError)
		return
	}
	respondSessions(w, sessions)
}

// GET /api/v1/private-shows/broadcaster/history?page=0&size=20
func (h *Handler) broadcasterHistory(w http.ResponseWriter, r *http.Request) {
	claims, ok := h.requireAuth(w, r)
	if !ok {
		return
	}
	limit, offset := pagination(r, 20, 100)
	sessions, err := h.svc.ListBroadcasterHistory(r.Context(), claims.UserID, limit, offset)
	if err != nil {
		log.Error().Err(err).Str("broadcaster_id", claims.UserID.String()).Msg("broadcaster history")
		jsonErr(w, "internal error", http.StatusInternalServerError)
		return
	}
	respondSessions(w, sessions)
}

func (h *Handler) health(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "UP"})
}

func (h *Handler) ready(w http.ResponseWriter, r *http.Request) {
	if err := h.db.Ping(r.Context()); err != nil {
		http.Error(w, `{"status":"DOWN","reason":"database not ready"}`, http.StatusServiceUnavailable)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "READY"})
}

// ── response types ────────────────────────────────────────────────────────────

type sessionResp struct {
	ID            uuid.UUID  `json:"id"`
	ViewerID      uuid.UUID  `json:"viewerId"`
	BroadcasterID uuid.UUID  `json:"broadcasterId"`
	RoomID        uuid.UUID  `json:"roomId"`
	ShowType      string     `json:"showType"`
	Status        string     `json:"status"`
	RatePerMinute int64      `json:"ratePerMinute"`
	BilledMinutes int        `json:"billedMinutes"`
	TotalTokens   int64      `json:"totalTokens"`
	StartedAt     time.Time  `json:"startedAt"`
	EndedAt       *time.Time `json:"endedAt,omitempty"`
	EndReason     *string    `json:"endReason,omitempty"`
}

func sessionResponse(s *model.Session) *sessionResp {
	r := &sessionResp{
		ID: s.ID, ViewerID: s.ViewerID, BroadcasterID: s.BroadcasterID, RoomID: s.RoomID,
		ShowType: string(s.ShowType), Status: string(s.Status),
		RatePerMinute: s.RatePerMinute, BilledMinutes: s.BilledMinutes,
		TotalTokens: s.TotalTokens, StartedAt: s.StartedAt, EndedAt: s.EndedAt,
	}
	if s.EndReason != nil {
		er := string(*s.EndReason)
		r.EndReason = &er
	}
	return r
}

func respondSessions(w http.ResponseWriter, sessions []*model.Session) {
	out := make([]*sessionResp, len(sessions))
	for i, s := range sessions {
		out[i] = sessionResponse(s)
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]any{"items": out, "count": len(out)})
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

func handleSessionErr(w http.ResponseWriter, err error, _ uuid.UUID) {
	switch {
	case errors.Is(err, billing.ErrSessionNotFound):
		jsonErr(w, "session not found", http.StatusNotFound)
	case errors.Is(err, billing.ErrNotAuthorized):
		jsonErr(w, "forbidden", http.StatusForbidden)
	default:
		log.Error().Err(err).Msg("session operation")
		jsonErr(w, "internal error", http.StatusInternalServerError)
	}
}

func jsonErr(w http.ResponseWriter, msg string, code int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(map[string]string{"error": msg})
}

func jsonOK(w http.ResponseWriter) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]bool{"ok": true})
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
