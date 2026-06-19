package service

import (
	"context"
	"time"

	"github.com/google/uuid"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"

	"github.com/platform/streaming-orchestration-service/internal/domain"
	"github.com/platform/streaming-orchestration-service/internal/kafka"
	"github.com/platform/streaming-orchestration-service/internal/provider"
	"github.com/platform/streaming-orchestration-service/internal/store"
)

// Orchestrator is the core business-logic layer.
// It wires provider (IVS/Mock) → DB (rooms, sessions) → Redis cache → Kafka events.
type Orchestrator struct {
	db               *store.DB
	redis            *store.RedisStore
	streamingProvider provider.StreamingProvider
	producer         *kafka.Producer
	defaultThreshold int64
	logger           zerolog.Logger
}

func NewOrchestrator(
	db *store.DB,
	redis *store.RedisStore,
	sp provider.StreamingProvider,
	producer *kafka.Producer,
	defaultThreshold int64,
) *Orchestrator {
	return &Orchestrator{
		db:               db,
		redis:            redis,
		streamingProvider: sp,
		producer:         producer,
		defaultThreshold: defaultThreshold,
		logger:           log.With().Str("component", "orchestrator").Logger(),
	}
}

// ── Public API ────────────────────────────────────────────────────────────────

// CreateRoom provisions a streaming channel in the provider and persists the room.
func (o *Orchestrator) CreateRoom(ctx context.Context, broadcasterID uuid.UUID, title string, threshold int64) (*domain.Room, error) {
	if threshold <= 0 {
		threshold = o.defaultThreshold
	}

	roomID := uuid.New()
	result, err := o.streamingProvider.CreateChannel(ctx, roomID.String(), broadcasterID.String())
	if err != nil {
		return nil, err
	}

	room := &domain.Room{
		ID:                 roomID,
		BroadcasterID:      broadcasterID,
		Title:              title,
		Status:             domain.RoomStatusOffline,
		DeliveryMode:       domain.DeliveryModeWebRTC,
		IngestEndpoint:     result.IngestEndpoint,
		StreamKey:          result.StreamKey,
		HLSPlaybackURL:     result.HLSPlaybackURL,
		Provider:           domain.Provider(o.providerName()),
		ProviderChannelID:  result.ChannelID,
		PromotionThreshold: threshold,
	}
	if err := o.db.CreateRoom(ctx, room); err != nil {
		// Best-effort cleanup: try to delete the provider channel.
		o.streamingProvider.DeleteChannel(ctx, result.ChannelID)
		return nil, err
	}

	o.logger.Info().
		Str("room_id", room.ID.String()).
		Str("broadcaster_id", broadcasterID.String()).
		Str("provider", string(room.Provider)).
		Msg("room created")

	return room, nil
}

// StartStream marks a room as LIVE and creates a StreamSession.
func (o *Orchestrator) StartStream(ctx context.Context, roomID, broadcasterID uuid.UUID) (*domain.Room, error) {
	room, err := o.db.GetRoom(ctx, roomID)
	if err != nil {
		return nil, err
	}
	if room.BroadcasterID != broadcasterID {
		return nil, domain.ErrNotAuthorized{}
	}
	if room.Status == domain.RoomStatusLive {
		return nil, domain.ErrRoomAlreadyLive{}
	}

	if err := o.db.SetRoomLive(ctx, roomID, room.Version); err != nil {
		return nil, err
	}

	session := &domain.StreamSession{
		ID:            uuid.New(),
		RoomID:        roomID,
		BroadcasterID: broadcasterID,
	}
	if err := o.db.CreateSession(ctx, session); err != nil {
		return nil, err
	}

	room.Status = domain.RoomStatusLive
	o.redis.CacheRoom(ctx, room)

	o.producer.PublishStreamStarted(ctx,
		roomID.String(), broadcasterID.String(),
		session.ID.String(), room.HLSPlaybackURL)

	o.logger.Info().
		Str("room_id", roomID.String()).
		Str("session_id", session.ID.String()).
		Msg("stream started")

	return room, nil
}

// StopStream marks a room as OFFLINE, ends the active session, and signals the provider.
func (o *Orchestrator) StopStream(ctx context.Context, roomID, broadcasterID uuid.UUID) (*domain.Room, error) {
	room, err := o.db.GetRoom(ctx, roomID)
	if err != nil {
		return nil, err
	}
	if room.BroadcasterID != broadcasterID {
		return nil, domain.ErrNotAuthorized{}
	}
	if room.Status == domain.RoomStatusOffline {
		return room, nil // idempotent
	}

	session, _ := o.db.GetActiveSession(ctx, roomID)

	// Signal the provider (best-effort; stream may have already disconnected).
	if err := o.streamingProvider.StopStream(ctx, room.ProviderChannelID); err != nil {
		o.logger.Warn().Err(err).Str("room_id", roomID.String()).Msg("provider stop stream failed (continuing)")
	}

	if err := o.db.SetRoomOffline(ctx, roomID, room.Version); err != nil {
		return nil, err
	}
	room.Status = domain.RoomStatusOffline
	room.DeliveryMode = domain.DeliveryModeWebRTC

	var peakViewers int64
	if session != nil {
		peakViewers = room.PeakViewerCount
		o.db.EndSession(ctx, roomID, peakViewers)
	}

	o.redis.InvalidateRoom(ctx, roomID.String())

	sessionID := ""
	if session != nil {
		sessionID = session.ID.String()
	}
	o.producer.PublishStreamEnded(ctx, roomID.String(), broadcasterID.String(), sessionID, peakViewers)

	o.logger.Info().
		Str("room_id", roomID.String()).
		Int64("peak_viewers", peakViewers).
		Msg("stream ended")

	return room, nil
}

// GetRoom returns room state, checking the Redis cache first.
func (o *Orchestrator) GetRoom(ctx context.Context, roomID uuid.UUID) (*domain.Room, error) {
	return o.db.GetRoom(ctx, roomID)
}

// GetPlayback returns the active playback URL and delivery mode for a viewer.
// This is the hot path — checks Redis first, falls back to Postgres on cache miss.
type PlaybackInfo struct {
	RoomID       string
	Status       string
	DeliveryMode string
	PlaybackURL  string
	ViewerCount  int64
}

func (o *Orchestrator) GetPlayback(ctx context.Context, roomID uuid.UUID) (*PlaybackInfo, error) {
	// Fast path: Redis cache
	cached, err := o.redis.GetCachedRoom(ctx, roomID.String())
	if err == nil && cached != nil {
		return &PlaybackInfo{
			RoomID:       roomID.String(),
			Status:       cached.Status,
			DeliveryMode: cached.DeliveryMode,
			PlaybackURL:  cached.PlaybackURL,
			ViewerCount:  cached.ViewerCount,
		}, nil
	}

	// Cache miss: read from Postgres and re-populate cache.
	room, err := o.db.GetRoom(ctx, roomID)
	if err != nil {
		return nil, err
	}
	o.redis.CacheRoom(ctx, room)

	return &PlaybackInfo{
		RoomID:       room.ID.String(),
		Status:       string(room.Status),
		DeliveryMode: string(room.DeliveryMode),
		PlaybackURL:  room.PlaybackURL(),
		ViewerCount:  room.ViewerCount,
	}, nil
}

func (o *Orchestrator) ListRooms(ctx context.Context, broadcasterID uuid.UUID) ([]*domain.Room, error) {
	return o.db.ListRoomsByBroadcaster(ctx, broadcasterID)
}

func (o *Orchestrator) UpdateTitle(ctx context.Context, roomID, broadcasterID uuid.UUID, title string) error {
	room, err := o.db.GetRoom(ctx, roomID)
	if err != nil {
		return err
	}
	if room.BroadcasterID != broadcasterID {
		return domain.ErrNotAuthorized{}
	}
	return o.db.UpdateRoomTitle(ctx, roomID, title)
}

// UpdateViewerCount is called by the Kafka presence consumer on each ROOM_COUNT_SNAPSHOT.
// It updates the Redis cache + DB stats and checks the WebRTC → LL-HLS promotion threshold.
func (o *Orchestrator) UpdateViewerCount(ctx context.Context, roomIDStr string, count int64) {
	roomID, err := uuid.Parse(roomIDStr)
	if err != nil {
		return
	}

	// Update Redis cache immediately (low latency).
	o.redis.SetViewerCount(ctx, roomIDStr, count)

	// Read from Postgres to get room state and check promotion.
	room, err := o.db.GetRoom(ctx, roomID)
	if err != nil || room.Status != domain.RoomStatusLive {
		return
	}

	// Persist viewer stats (updates both viewer_count and peak_viewer_count).
	o.db.UpdateViewerStats(ctx, roomID, count)

	switch {
	case room.DeliveryMode == domain.DeliveryModeWebRTC && count >= room.PromotionThreshold:
		o.promoteToHLS(ctx, room, count)
	case room.DeliveryMode == domain.DeliveryModeHLS && count < room.PromotionThreshold/2:
		// Hysteresis: demote only when count drops well below threshold.
		o.demoteToWebRTC(ctx, room, count)
	}
}

// ── Internal helpers ──────────────────────────────────────────────────────────

func (o *Orchestrator) promoteToHLS(ctx context.Context, room *domain.Room, viewers int64) {
	if err := o.db.SetDeliveryMode(ctx, room.ID, domain.DeliveryModeHLS); err != nil {
		o.logger.Error().Err(err).Str("room_id", room.ID.String()).Msg("DB promotion failed")
		return
	}
	o.redis.SetDeliveryMode(ctx, room.ID.String(), domain.DeliveryModeHLS, room.HLSPlaybackURL)
	o.producer.PublishPromotedToHLS(ctx, room.ID.String(), room.BroadcasterID.String(), room.HLSPlaybackURL, viewers)

	o.logger.Info().
		Str("room_id", room.ID.String()).
		Int64("viewer_count", viewers).
		Int64("threshold", room.PromotionThreshold).
		Msg("room promoted to LL-HLS")
}

func (o *Orchestrator) demoteToWebRTC(ctx context.Context, room *domain.Room, viewers int64) {
	webRTCURL := room.WebRTCPlaybackURL
	if webRTCURL == "" {
		webRTCURL = room.HLSPlaybackURL // Phase 1: no WebRTC playback URL, keep HLS
	}
	if err := o.db.SetDeliveryMode(ctx, room.ID, domain.DeliveryModeWebRTC); err != nil {
		o.logger.Error().Err(err).Str("room_id", room.ID.String()).Msg("DB demotion failed")
		return
	}
	o.redis.SetDeliveryMode(ctx, room.ID.String(), domain.DeliveryModeWebRTC, webRTCURL)
	o.producer.PublishDemotedToWebRTC(ctx, room.ID.String(), room.BroadcasterID.String(), viewers)

	o.logger.Info().
		Str("room_id", room.ID.String()).
		Int64("viewer_count", viewers).
		Msg("room demoted back to WebRTC")
}

func (o *Orchestrator) providerName() string {
	switch o.streamingProvider.(type) {
	case *provider.IVSProvider:
		return string(domain.ProviderIVS)
	case *provider.MockProvider:
		return string(domain.ProviderMock)
	default:
		return "UNKNOWN"
	}
}

// HealthCheck validates DB and Redis connectivity.
func (o *Orchestrator) HealthCheck(ctx context.Context) error {
	if err := o.db.Ping(ctx); err != nil {
		return err
	}
	return o.redis.Ping(ctx)
}

// Ensure Orchestrator satisfies the kafka.ViewerCountHandler interface.
var _ interface {
	UpdateViewerCount(ctx context.Context, roomID string, count int64)
} = (*Orchestrator)(nil)

// Keep track of promotion timing to avoid spamming the DB on every heartbeat.
type promotionRecord struct {
	lastChecked time.Time
}
