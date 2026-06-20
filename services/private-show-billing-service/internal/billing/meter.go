package billing

import (
	"context"
	"math"
	"time"

	"github.com/google/uuid"
	"github.com/rs/zerolog/log"

	redisstore "github.com/platform/private-show-billing-service/internal/redis"
)

// Meter is a background poller that scans all active sessions every interval
// and executes a billing tick whenever a session's elapsed time exceeds its
// billed_minutes count.
//
// This design is crash-safe: on restart, sessions are re-read from Redis and
// the meter resumes. Idempotency keys in the wallet transfer prevent double billing
// even if two replicas race.
type Meter struct {
	service  *Service
	redis    *redisstore.Store
	interval time.Duration
}

func NewMeter(svc *Service, redis *redisstore.Store, intervalSec int) *Meter {
	return &Meter{
		service:  svc,
		redis:    redis,
		interval: time.Duration(intervalSec) * time.Second,
	}
}

// Run polls active sessions until ctx is cancelled.
func (m *Meter) Run(ctx context.Context) error {
	ticker := time.NewTicker(m.interval)
	defer ticker.Stop()

	logger := log.With().Str("component", "billing-meter").Logger()
	logger.Info().Dur("interval", m.interval).Msg("billing meter started")

	for {
		select {
		case <-ctx.Done():
			logger.Info().Msg("billing meter stopped")
			return nil
		case <-ticker.C:
			m.poll(ctx)
		}
	}
}

func (m *Meter) poll(ctx context.Context) {
	ids, err := m.redis.ActiveSessionIDs(ctx)
	if err != nil {
		log.Error().Err(err).Msg("billing meter: list active sessions")
		return
	}

	for _, idStr := range ids {
		sid, err := uuid.Parse(idStr)
		if err != nil {
			continue
		}
		rsess, err := m.redis.GetSession(ctx, sid)
		if err != nil || rsess == nil {
			continue
		}
		m.maybeTickSession(ctx, rsess)
	}
}

// maybeTickSession bills a minute if the session's elapsed time has crossed
// the next billing boundary (i.e. it has been running for at least one more
// minute than it has been billed for).
func (m *Meter) maybeTickSession(ctx context.Context, rsess *redisstore.RedisSession) {
	elapsedSec := time.Since(rsess.StartedAt).Seconds()
	expectedMinutes := int(math.Floor(elapsedSec / 60))

	if expectedMinutes <= rsess.BilledMinutes {
		return // not yet due for the next tick
	}

	if _, err := m.service.ExecuteTick(ctx, rsess); err != nil {
		log.Error().Err(err).
			Str("session_id", rsess.SessionID.String()).
			Msg("billing meter: execute tick")
	}
}
