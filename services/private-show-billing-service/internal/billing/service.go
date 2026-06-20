package billing

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/rs/zerolog/log"

	"github.com/platform/private-show-billing-service/internal/client"
	"github.com/platform/private-show-billing-service/internal/db"
	"github.com/platform/private-show-billing-service/internal/kafka"
	"github.com/platform/private-show-billing-service/internal/model"
	redisstore "github.com/platform/private-show-billing-service/internal/redis"
)

var (
	ErrSessionNotFound    = errors.New("session not found")
	ErrViewerHasSession   = errors.New("viewer already has an active private show")
	ErrNotAuthorized      = errors.New("not authorized to end this session")
)

type Service struct {
	db       *db.Postgres
	redis    *redisstore.Store
	wallet   *client.WalletClient
	producer *kafka.Producer
}

func NewService(database *db.Postgres, redis *redisstore.Store,
	wallet *client.WalletClient, producer *kafka.Producer) *Service {
	return &Service{db: database, redis: redis, wallet: wallet, producer: producer}
}

// StartSession creates a new private show billing session.
// A viewer can only have one active session at a time.
func (s *Service) StartSession(ctx context.Context,
	viewerID, broadcasterID, roomID uuid.UUID,
	showType model.ShowType, ratePerMinute int64) (*model.Session, error) {

	// Enforce single active session per viewer
	existing, err := s.redis.GetViewerActiveSession(ctx, viewerID)
	if err != nil {
		return nil, fmt.Errorf("check viewer session: %w", err)
	}
	if existing != "" {
		return nil, ErrViewerHasSession
	}

	sess := &model.Session{
		ID:            uuid.New(),
		ViewerID:      viewerID,
		BroadcasterID: broadcasterID,
		RoomID:        roomID,
		ShowType:      showType,
		Status:        model.StatusActive,
		RatePerMinute: ratePerMinute,
		StartedAt:     time.Now().UTC(),
	}

	if err := s.db.InsertSession(ctx, sess); err != nil {
		return nil, fmt.Errorf("insert session: %w", err)
	}
	if err := s.redis.CreateSession(ctx, sess); err != nil {
		return nil, fmt.Errorf("cache session: %w", err)
	}

	s.producer.PublishStarted(ctx, &kafka.PrivateShowEvent{
		SessionID:     sess.ID,
		ViewerID:      viewerID,
		BroadcasterID: broadcasterID,
		RoomID:        roomID,
		ShowType:      string(showType),
		RatePerMinute: ratePerMinute,
	})

	log.Info().
		Str("session_id", sess.ID.String()).
		Str("viewer_id", viewerID.String()).
		Str("broadcaster_id", broadcasterID.String()).
		Int64("rate_per_minute", ratePerMinute).
		Msg("private show started")

	return sess, nil
}

// EndSession ends a session — called by viewer, broadcaster, or the system.
func (s *Service) EndSession(ctx context.Context, sessionID, requestingUserID uuid.UUID,
	reason model.EndReason) (*model.Session, error) {

	rsess, err := s.redis.GetSession(ctx, sessionID)
	if err != nil {
		return nil, fmt.Errorf("redis get session: %w", err)
	}

	// Fall back to DB if Redis entry has already expired
	var dbSess *model.Session
	if rsess == nil {
		dbSess, err = s.db.GetSession(ctx, sessionID)
		if err != nil {
			return nil, ErrSessionNotFound
		}
		if dbSess.Status == model.StatusCompleted {
			return dbSess, nil // already ended — idempotent
		}
		// Reconcile from DB (session was active but Redis was flushed)
		billedMinutes, _ := s.db.GetTickCount(ctx, sessionID)
		totalTokens := int64(billedMinutes) * dbSess.RatePerMinute
		_ = s.db.UpdateSessionEnd(ctx, sessionID, billedMinutes, totalTokens, reason)
		return dbSess, nil
	}

	// Auth check: only the viewer, broadcaster, or system (STREAM_ENDED) can end
	if reason != model.EndReasonStreamEnded &&
		requestingUserID != rsess.ViewerID &&
		requestingUserID != rsess.BroadcasterID {
		return nil, ErrNotAuthorized
	}

	billedMinutes := rsess.BilledMinutes
	totalTokens := int64(billedMinutes) * rsess.RatePerMinute

	if err := s.db.UpdateSessionEnd(ctx, sessionID, billedMinutes, totalTokens, reason); err != nil {
		return nil, fmt.Errorf("update session end: %w", err)
	}
	if err := s.redis.DeleteSession(ctx, rsess); err != nil {
		log.Warn().Err(err).Str("session_id", sessionID.String()).Msg("delete session from redis")
	}

	s.producer.PublishEnded(ctx, &kafka.PrivateShowEvent{
		SessionID:     sessionID,
		ViewerID:      rsess.ViewerID,
		BroadcasterID: rsess.BroadcasterID,
		RoomID:        rsess.RoomID,
		BilledMinutes: billedMinutes,
		TotalTokens:   totalTokens,
		EndReason:     string(reason),
	})

	log.Info().
		Str("session_id", sessionID.String()).
		Int("billed_minutes", billedMinutes).
		Int64("total_tokens", totalTokens).
		Str("reason", string(reason)).
		Msg("private show ended")

	return s.db.GetSession(ctx, sessionID)
}

// PauseSession pauses billing (e.g. broadcaster paused the stream).
func (s *Service) PauseSession(ctx context.Context, sessionID, requestingUserID uuid.UUID) error {
	rsess, err := s.redis.GetSession(ctx, sessionID)
	if err != nil || rsess == nil {
		return ErrSessionNotFound
	}
	if requestingUserID != rsess.ViewerID && requestingUserID != rsess.BroadcasterID {
		return ErrNotAuthorized
	}
	if err := s.redis.SetStatus(ctx, sessionID, model.StatusPaused); err != nil {
		return err
	}
	if err := s.db.UpdateSessionPause(ctx, sessionID, true); err != nil {
		return err
	}
	s.producer.PublishPaused(ctx, &kafka.PrivateShowEvent{
		SessionID: sessionID, ViewerID: rsess.ViewerID,
		BroadcasterID: rsess.BroadcasterID, RoomID: rsess.RoomID,
	})
	return nil
}

// ResumeSession resumes billing after a pause.
func (s *Service) ResumeSession(ctx context.Context, sessionID, requestingUserID uuid.UUID) error {
	rsess, err := s.redis.GetSession(ctx, sessionID)
	if err != nil || rsess == nil {
		return ErrSessionNotFound
	}
	if requestingUserID != rsess.ViewerID && requestingUserID != rsess.BroadcasterID {
		return ErrNotAuthorized
	}
	if err := s.redis.SetStatus(ctx, sessionID, model.StatusActive); err != nil {
		return err
	}
	if err := s.db.UpdateSessionPause(ctx, sessionID, false); err != nil {
		return err
	}
	s.producer.PublishResumed(ctx, &kafka.PrivateShowEvent{
		SessionID: sessionID, ViewerID: rsess.ViewerID,
		BroadcasterID: rsess.BroadcasterID, RoomID: rsess.RoomID,
	})
	return nil
}

// HandleStreamEnded auto-ends all active sessions for a given room (called by Kafka consumer).
func (s *Service) HandleStreamEnded(ctx context.Context, roomID string) {
	ids, err := s.redis.ActiveSessionIDs(ctx)
	if err != nil {
		log.Error().Err(err).Str("room_id", roomID).Msg("list active sessions for stream end")
		return
	}
	for _, idStr := range ids {
		sid, err := uuid.Parse(idStr)
		if err != nil {
			continue
		}
		rsess, err := s.redis.GetSession(ctx, sid)
		if err != nil || rsess == nil || rsess.RoomID.String() != roomID {
			continue
		}
		if _, err := s.EndSession(ctx, sid, uuid.Nil, model.EndReasonStreamEnded); err != nil {
			log.Error().Err(err).Str("session_id", idStr).Msg("auto-end session on stream ended")
		}
	}
}

// GetSession returns session data (Redis for active, DB for history).
func (s *Service) GetSession(ctx context.Context, sessionID uuid.UUID) (*model.Session, error) {
	rsess, err := s.redis.GetSession(ctx, sessionID)
	if err != nil {
		return nil, err
	}
	if rsess != nil {
		// Return live state: DB record + Redis billed_minutes
		sess, err := s.db.GetSession(ctx, sessionID)
		if err != nil {
			return nil, err
		}
		sess.BilledMinutes = rsess.BilledMinutes
		sess.TotalTokens = int64(rsess.BilledMinutes) * rsess.RatePerMinute
		sess.Status = rsess.Status
		return sess, nil
	}
	// Fall back to DB for completed sessions
	return s.db.GetSession(ctx, sessionID)
}

// ExecuteTick performs a single billing tick for a session.
// Called by the background billing meter for each due minute.
// Returns (false, nil) if session is paused or tick is a duplicate.
func (s *Service) ExecuteTick(ctx context.Context, rsess *redisstore.RedisSession) (bool, error) {
	if rsess.Status == model.StatusPaused {
		return false, nil
	}

	minuteNumber := rsess.BilledMinutes + 1
	idempotencyKey := fmt.Sprintf("%s-tick-%d", rsess.SessionID.String(), minuteNumber)

	result, err := s.wallet.Transfer(ctx,
		rsess.ViewerID, rsess.BroadcasterID,
		rsess.RatePerMinute, rsess.SessionID, idempotencyKey)

	if err == client.ErrInsufficientFunds {
		log.Warn().
			Str("session_id", rsess.SessionID.String()).
			Str("viewer_id", rsess.ViewerID.String()).
			Msg("insufficient funds — ending private show")

		s.producer.PublishInsufficientFunds(ctx, &kafka.PrivateShowEvent{
			SessionID: rsess.SessionID, ViewerID: rsess.ViewerID,
			BroadcasterID: rsess.BroadcasterID, RoomID: rsess.RoomID,
		})
		_, _ = s.EndSession(ctx, rsess.SessionID, uuid.Nil, model.EndReasonInsufficientFunds)
		return false, nil
	}
	if err != nil {
		return false, fmt.Errorf("wallet transfer: %w", err)
	}

	// Atomically increment the Redis counter
	newMinutes, err := s.redis.IncrBilledMinutes(ctx, rsess.SessionID)
	if err != nil {
		log.Error().Err(err).Str("session_id", rsess.SessionID.String()).
			Msg("incrby billed_minutes failed after wallet transfer")
	}

	// Record the tick in PostgreSQL (ON CONFLICT DO NOTHING for idempotency)
	var walletTxID *uuid.UUID
	if result != nil {
		id := result.SenderTx.ID
		walletTxID = &id
	}
	tick := &model.BillingTick{
		ID:            uuid.New(),
		SessionID:     rsess.SessionID,
		ViewerID:      rsess.ViewerID,
		BroadcasterID: rsess.BroadcasterID,
		TokensCharged: rsess.RatePerMinute,
		MinuteNumber:  minuteNumber,
		WalletTxID:    walletTxID,
	}
	if err := s.db.InsertBillingTick(ctx, tick); err != nil {
		log.Error().Err(err).Str("session_id", rsess.SessionID.String()).
			Int("minute", minuteNumber).Msg("insert billing tick failed")
	}

	s.producer.PublishTickBilled(ctx, &kafka.PrivateShowEvent{
		SessionID:     rsess.SessionID,
		ViewerID:      rsess.ViewerID,
		BroadcasterID: rsess.BroadcasterID,
		RoomID:        rsess.RoomID,
		TokensCharged: rsess.RatePerMinute,
		MinuteNumber:  int(newMinutes),
	})

	log.Debug().
		Str("session_id", rsess.SessionID.String()).
		Int64("minute", newMinutes).
		Int64("tokens", rsess.RatePerMinute).
		Msg("billing tick processed")

	return true, nil
}

// ListViewerHistory returns paginated completed sessions for a viewer.
func (s *Service) ListViewerHistory(ctx context.Context, viewerID uuid.UUID, limit, offset int) ([]*model.Session, error) {
	return s.db.ListSessionsByViewer(ctx, viewerID, limit, offset)
}

// ListBroadcasterHistory returns paginated completed sessions for a broadcaster.
func (s *Service) ListBroadcasterHistory(ctx context.Context, broadcasterID uuid.UUID, limit, offset int) ([]*model.Session, error) {
	return s.db.ListSessionsByBroadcaster(ctx, broadcasterID, limit, offset)
}
