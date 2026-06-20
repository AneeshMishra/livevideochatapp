package notification

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/google/uuid"
	"github.com/rs/zerolog/log"

	"github.com/platform/notification-service/internal/channel"
	"github.com/platform/notification-service/internal/db"
	"github.com/platform/notification-service/internal/model"
	redisstore "github.com/platform/notification-service/internal/redis"
)

// Service dispatches a NotificationRequest through eligible channels.
// Flow per channel:
//  1. Check user preference (opt-out model — enabled by default).
//  2. Deduplication check via Redis.
//  3. Rate limit check (bypassed for CRITICAL priority).
//  4. Fetch device tokens / email address (for PUSH / EMAIL).
//  5. Send via channel adapter.
//  6. Write delivery log to PostgreSQL.
type Service struct {
	db               *db.Postgres
	redis            *redisstore.Store
	emailSender      channel.EmailSender
	pushSender       channel.PushSender
	pushRatePerHour  int
	emailRatePerDay  int
}

func NewService(
	database *db.Postgres,
	redis *redisstore.Store,
	emailSender channel.EmailSender,
	pushSender channel.PushSender,
	pushRatePerHour, emailRatePerDay int,
) *Service {
	return &Service{
		db:              database,
		redis:           redis,
		emailSender:     emailSender,
		pushSender:      pushSender,
		pushRatePerHour: pushRatePerHour,
		emailRatePerDay: emailRatePerDay,
	}
}

// Process dispatches a single NotificationRequest to all eligible channels.
func (s *Service) Process(ctx context.Context, req *model.NotificationRequest) {
	logger := log.With().
		Str("event_type", req.EventType).
		Str("recipient_id", req.RecipientID.String()).
		Logger()

	for _, ch := range req.Channels {
		status, errMsg := s.dispatchChannel(ctx, req, ch)
		s.writeLog(ctx, req, ch, status, errMsg)
		if status == model.StatusSent {
			logger.Info().Str("channel", ch).Msg("notification sent")
		} else {
			logger.Debug().Str("channel", ch).Str("status", status).Msg("notification skipped")
		}
	}
}

func (s *Service) dispatchChannel(ctx context.Context, req *model.NotificationRequest, ch string) (status, errMsg string) {
	isCritical := req.Priority == model.PriorityCritical

	// 1. User preference check (skip for CRITICAL)
	if !isCritical {
		enabled, err := s.db.IsChannelEnabled(ctx, req.RecipientID, req.EventType, ch)
		if err != nil || !enabled {
			return model.StatusSkipped, ""
		}
	}

	// 2. Deduplication
	if req.DedupeKey != "" {
		duped, err := s.redis.IsDuplicate(ctx, req.RecipientID, req.DedupeKey+":"+ch)
		if err != nil {
			log.Warn().Err(err).Str("key", req.DedupeKey).Msg("dedup check failed — allowing through")
		} else if duped {
			return model.StatusDeduped, ""
		}
	}

	switch ch {
	case model.ChannelPush:
		return s.sendPush(ctx, req, isCritical)
	case model.ChannelEmail:
		return s.sendEmail(ctx, req, isCritical)
	case model.ChannelInApp:
		return s.sendInApp(ctx, req)
	default:
		return model.StatusSkipped, fmt.Sprintf("unknown channel: %s", ch)
	}
}

func (s *Service) sendPush(ctx context.Context, req *model.NotificationRequest, isCritical bool) (string, string) {
	// Rate limit (bypassed for CRITICAL)
	if !isCritical {
		allowed, err := s.redis.CheckAndIncrPush(ctx, req.RecipientID, s.pushRatePerHour)
		if err != nil {
			log.Warn().Err(err).Msg("push rate limit check failed")
		} else if !allowed {
			return model.StatusRateLimited, ""
		}
	}

	tokens, err := s.db.GetActiveDeviceTokens(ctx, req.RecipientID)
	if err != nil || len(tokens) == 0 {
		return model.StatusSkipped, "no active device tokens"
	}

	metaData := make(map[string]any)
	for k, v := range req.Metadata {
		metaData[k] = v
	}
	metaData["eventType"] = req.EventType

	var lastErr error
	sent := 0
	for _, token := range tokens {
		if err := s.pushSender.Send(ctx, token.Token, req.Title, req.Body, metaData); err != nil {
			log.Warn().Err(err).Str("token_prefix", token.Token[:min(12, len(token.Token))]).Msg("push send failed")
			lastErr = err
		} else {
			sent++
		}
	}
	if sent == 0 {
		return model.StatusFailed, fmt.Sprintf("all %d tokens failed: %v", len(tokens), lastErr)
	}
	return model.StatusSent, ""
}

func (s *Service) sendEmail(ctx context.Context, req *model.NotificationRequest, isCritical bool) (string, string) {
	// Rate limit (bypassed for CRITICAL)
	if !isCritical {
		allowed, err := s.redis.CheckAndIncrEmail(ctx, req.RecipientID, s.emailRatePerDay)
		if err != nil {
			log.Warn().Err(err).Msg("email rate limit check failed")
		} else if !allowed {
			return model.StatusRateLimited, ""
		}
	}

	// In production, look up user email from identity-auth-service.
	// For Phase 1: use a placeholder; the mock sender just logs it.
	toEmail := fmt.Sprintf("%s@platform.internal", req.RecipientID)

	if err := s.emailSender.Send(ctx, req.RecipientID.String(), toEmail, req.Title, req.Body); err != nil {
		return model.StatusFailed, err.Error()
	}
	return model.StatusSent, ""
}

func (s *Service) sendInApp(ctx context.Context, req *model.NotificationRequest) (string, string) {
	payload, err := json.Marshal(map[string]any{
		"eventType": req.EventType,
		"title":     req.Title,
		"body":      req.Body,
		"metadata":  req.Metadata,
		"priority":  req.Priority,
	})
	if err != nil {
		return model.StatusFailed, err.Error()
	}
	if err := s.redis.PublishInApp(ctx, req.RecipientID, string(payload)); err != nil {
		return model.StatusFailed, err.Error()
	}
	return model.StatusSent, ""
}

func (s *Service) writeLog(ctx context.Context, req *model.NotificationRequest, ch, status, errMsg string) {
	meta := "{}"
	if req.Metadata != nil {
		if b, err := json.Marshal(req.Metadata); err == nil {
			meta = string(b)
		}
	}
	entry := &model.NotificationLog{
		ID:        uuid.New(),
		UserID:    req.RecipientID,
		EventType: req.EventType,
		Channel:   ch,
		Status:    status,
		Title:     req.Title,
		Body:      req.Body,
		Metadata:  meta,
		Error:     errMsg,
	}
	if err := s.db.InsertLog(ctx, entry); err != nil {
		log.Error().Err(err).Str("user_id", req.RecipientID.String()).Msg("write notification log")
	}
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
