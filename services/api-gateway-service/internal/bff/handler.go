package bff

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"time"

	"github.com/rs/zerolog/log"
	"golang.org/x/sync/errgroup"
)

type Handler struct {
	catalogURL  string
	presenceURL string
	streamURL   string
	broadcURL   string
	httpClient  *http.Client
}

func NewHandler(catalogURL, presenceURL, streamURL, broadcURL string) *Handler {
	return &Handler{
		catalogURL:  catalogURL,
		presenceURL: presenceURL,
		streamURL:   streamURL,
		broadcURL:   broadcURL,
		httpClient: &http.Client{
			Timeout: 5 * time.Second,
		},
	}
}

// GET /api/v1/bff/home
// Aggregates the live catalog grid and platform-level presence counts in a
// single response so the web client makes one request instead of two.
func (h *Handler) Home(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 4*time.Second)
	defer cancel()

	var (
		catalogBody  []byte
		presenceBody []byte
	)

	g, ctx := errgroup.WithContext(ctx)

	g.Go(func() error {
		body, err := h.get(ctx, h.catalogURL+"/api/v1/catalog/rooms", r.Header.Get("Authorization"))
		if err != nil {
			log.Warn().Err(err).Msg("bff/home: catalog fetch failed")
			body = []byte(`{"rooms":[],"total":0}`)
		}
		catalogBody = body
		return nil
	})

	g.Go(func() error {
		body, err := h.get(ctx, h.presenceURL+"/api/v1/presence/stats", "")
		if err != nil {
			log.Warn().Err(err).Msg("bff/home: presence fetch failed")
			body = []byte(`{"online_users":0,"active_rooms":0}`)
		}
		presenceBody = body
		return nil
	})

	g.Wait() //nolint:errcheck — errors are handled inside each goroutine above

	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")

	enc := json.NewEncoder(w)
	enc.Encode(map[string]json.RawMessage{
		"catalog":  json.RawMessage(catalogBody),
		"presence": json.RawMessage(presenceBody),
	})
}

// GET /api/v1/bff/room/{roomId}
// Aggregates streaming status, broadcaster profile, and live viewer count for
// a single room, eliminating three separate round-trips from the client.
func (h *Handler) Room(w http.ResponseWriter, r *http.Request) {
	roomID := r.PathValue("roomId")
	if roomID == "" {
		http.Error(w, `{"error":"roomId required"}`, http.StatusBadRequest)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 4*time.Second)
	defer cancel()

	authHeader := r.Header.Get("Authorization")

	var (
		streamBody    []byte
		broadcBody    []byte
		presenceBody  []byte
	)

	g, ctx := errgroup.WithContext(ctx)

	g.Go(func() error {
		body, err := h.get(ctx, h.streamURL+"/api/v1/streaming/rooms/"+roomID, authHeader)
		if err != nil {
			log.Warn().Err(err).Str("room_id", roomID).Msg("bff/room: streaming fetch failed")
			body = []byte(`null`)
		}
		streamBody = body
		return nil
	})

	g.Go(func() error {
		body, err := h.get(ctx, h.presenceURL+"/api/v1/presence/rooms/"+roomID, "")
		if err != nil {
			log.Warn().Err(err).Str("room_id", roomID).Msg("bff/room: presence fetch failed")
			body = []byte(`{"viewer_count":0}`)
		}
		presenceBody = body
		return nil
	})

	// The streaming service returns broadcaster_id; we need it to fetch the
	// broadcaster profile. To avoid a sequential dependency we fetch both in
	// parallel using room_id as a proxy — broadcaster-service exposes
	// GET /api/v1/broadcasters/by-room/{roomId}.
	g.Go(func() error {
		body, err := h.get(ctx, h.broadcURL+"/api/v1/broadcasters/by-room/"+roomID, "")
		if err != nil {
			log.Warn().Err(err).Str("room_id", roomID).Msg("bff/room: broadcaster fetch failed")
			body = []byte(`null`)
		}
		broadcBody = body
		return nil
	})

	g.Wait() //nolint:errcheck

	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")

	json.NewEncoder(w).Encode(map[string]json.RawMessage{
		"stream":      json.RawMessage(streamBody),
		"broadcaster": json.RawMessage(broadcBody),
		"presence":    json.RawMessage(presenceBody),
	})
}

func (h *Handler) get(ctx context.Context, url, authHeader string) ([]byte, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	if authHeader != "" {
		req.Header.Set("Authorization", authHeader)
	}
	resp, err := h.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	return io.ReadAll(resp.Body)
}
