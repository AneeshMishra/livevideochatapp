package notification

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/rs/zerolog/log"

	"github.com/platform/notification-service/internal/client"
	"github.com/platform/notification-service/internal/model"
	redisstore "github.com/platform/notification-service/internal/redis"
)

// RawEvent is a message pulled from any Kafka topic.
type RawEvent struct {
	Topic string
	Value []byte
}

// Router maps raw Kafka events to one or more NotificationRequests.
// It holds the Redis store (for follower lookups) and broadcaster client (for display names).
type Router struct {
	redis             *redisstore.Store
	broadcasterClient *client.BroadcasterClient
}

func NewRouter(redis *redisstore.Store, bc *client.BroadcasterClient) *Router {
	return &Router{redis: redis, broadcasterClient: bc}
}

// Route converts a raw Kafka message into zero or more notification requests.
// It also handles follower-set maintenance (FOLLOWED_BROADCASTER, UNFOLLOWED_BROADCASTER).
func (r *Router) Route(ctx context.Context, ev RawEvent) []*model.NotificationRequest {
	var base map[string]any
	if err := json.Unmarshal(ev.Value, &base); err != nil {
		log.Warn().Err(err).Str("topic", ev.Topic).Msg("router: unmarshal event")
		return nil
	}

	eventType, _ := base["type"].(string)
	if eventType == "" {
		eventType, _ = base["eventType"].(string)
	}

	switch eventType {

	// ── Tipping events ────────────────────────────────────────────────────────

	case "TIP_RECEIVED":
		return r.onTipReceived(base)

	case "GIFT_SENT":
		return r.onGiftSent(base)

	case "TIP_GOAL_COMPLETED":
		return r.onGoalCompleted(base)

	// ── Streaming events ──────────────────────────────────────────────────────

	case "STREAM_STARTED":
		return r.onStreamStarted(ctx, base)

	// ── Moderation events ─────────────────────────────────────────────────────

	case "CONTENT_FLAGGED":
		return r.onContentFlagged(base)

	case "STREAM_SUSPENDED":
		return r.onStreamSuspended(base)

	case "CSAM_DETECTED":
		return r.onCsamDetected(base)

	// ── Private-show events ───────────────────────────────────────────────────

	case "PRIVATE_SHOW_STARTED":
		return r.onPrivateShowStarted(base)

	case "PRIVATE_SHOW_ENDED":
		return r.onPrivateShowEnded(base)

	case "PRIVATE_SHOW_INSUFFICIENT_FUNDS":
		return r.onPrivateShowInsufficientFunds(base)

	// ── User-profile events (also maintain Redis follower sets) ───────────────

	case "FOLLOWED_BROADCASTER":
		return r.onFollowedBroadcaster(ctx, base)

	case "UNFOLLOWED_BROADCASTER":
		r.onUnfollowedBroadcaster(ctx, base)
		return nil

	default:
		// Unknown event type — silently ignore
		return nil
	}
}

// ── Tipping handlers ──────────────────────────────────────────────────────────

func (r *Router) onTipReceived(e map[string]any) []*model.NotificationRequest {
	recipientID := parseUUID(e["recipientId"])
	if recipientID == uuid.Nil {
		return nil
	}
	sender, _ := e["senderDisplayName"].(string)
	tokens := formatTokens(e["tokenAmount"])
	msg, _ := e["message"].(string)
	tipID, _ := e["tipId"].(string)

	body := fmt.Sprintf("%s tipped %s tokens", sender, tokens)
	if msg != "" {
		body += ": " + msg
	}
	return []*model.NotificationRequest{{
		RecipientID: recipientID,
		EventType:   "TIP_RECEIVED",
		Title:       "You received a tip!",
		Body:        body,
		Channels:    []string{model.ChannelPush, model.ChannelInApp},
		Priority:    model.PriorityRealtime,
		DedupeKey:   "TIP_RECEIVED:" + tipID,
		Metadata:    map[string]any{"senderId": e["senderId"], "tokenAmount": e["tokenAmount"]},
	}}
}

func (r *Router) onGiftSent(e map[string]any) []*model.NotificationRequest {
	recipientID := parseUUID(e["recipientId"])
	if recipientID == uuid.Nil {
		return nil
	}
	sender, _ := e["senderDisplayName"].(string)
	giftName, _ := e["giftTypeName"].(string)
	giftSentID, _ := e["giftSentId"].(string)

	return []*model.NotificationRequest{{
		RecipientID: recipientID,
		EventType:   "GIFT_SENT",
		Title:       "You received a gift!",
		Body:        fmt.Sprintf("%s sent you a %s!", sender, giftName),
		Channels:    []string{model.ChannelPush, model.ChannelInApp},
		Priority:    model.PriorityRealtime,
		DedupeKey:   "GIFT_SENT:" + giftSentID,
		Metadata:    map[string]any{"giftTypeName": giftName, "animationType": e["animationType"]},
	}}
}

func (r *Router) onGoalCompleted(e map[string]any) []*model.NotificationRequest {
	recipientID := parseUUID(e["broadcasterId"])
	if recipientID == uuid.Nil {
		return nil
	}
	title, _ := e["goalTitle"].(string)
	goalID, _ := e["goalId"].(string)

	return []*model.NotificationRequest{{
		RecipientID: recipientID,
		EventType:   "TIP_GOAL_COMPLETED",
		Title:       "Goal completed!",
		Body:        fmt.Sprintf("Your tip goal '%s' has been completed!", title),
		Channels:    []string{model.ChannelPush, model.ChannelInApp},
		Priority:    model.PriorityRealtime,
		DedupeKey:   "GOAL_COMPLETED:" + goalID,
		Metadata:    map[string]any{"goalTitle": title},
	}}
}

// ── Streaming handlers ────────────────────────────────────────────────────────

func (r *Router) onStreamStarted(ctx context.Context, e map[string]any) []*model.NotificationRequest {
	broadcasterIDStr, _ := e["broadcasterId"].(string)
	broadcasterID, err := uuid.Parse(broadcasterIDStr)
	if err != nil || broadcasterID == uuid.Nil {
		return nil
	}

	// Get broadcaster display name (cache-first)
	name, found := r.redis.GetCachedBroadcasterName(ctx, broadcasterID)
	if !found {
		fetched, err := r.broadcasterClient.GetDisplayName(ctx, broadcasterID)
		if err != nil {
			log.Warn().Err(err).Str("broadcaster_id", broadcasterIDStr).Msg("fetch broadcaster name")
			name = "Your favorite broadcaster"
		} else {
			name = fetched
			r.redis.CacheBroadcasterName(ctx, broadcasterID, name)
		}
	}

	// Fan-out: one push notification per follower
	followers, err := r.redis.GetFollowerIDs(ctx, broadcasterID)
	if err != nil {
		log.Error().Err(err).Str("broadcaster_id", broadcasterIDStr).Msg("get follower IDs")
		return nil
	}

	roomID, _ := e["roomId"].(string)
	reqs := make([]*model.NotificationRequest, 0, len(followers))
	for _, followerID := range followers {
		reqs = append(reqs, &model.NotificationRequest{
			RecipientID: followerID,
			EventType:   "STREAM_STARTED",
			Title:       name + " is now live!",
			Body:        "Tap to watch now",
			Channels:    []string{model.ChannelPush},
			Priority:    model.PriorityRealtime,
			DedupeKey:   fmt.Sprintf("STREAM_STARTED:%s:%s", roomID, followerID),
			Metadata:    map[string]any{"broadcasterId": broadcasterIDStr, "roomId": roomID},
		})
	}
	return reqs
}

// ── Moderation handlers ───────────────────────────────────────────────────────

func (r *Router) onContentFlagged(e map[string]any) []*model.NotificationRequest {
	broadcasterID := parseUUID(e["broadcasterId"])
	if broadcasterID == uuid.Nil {
		return nil
	}
	itemID, _ := e["itemId"].(string)
	return []*model.NotificationRequest{{
		RecipientID: broadcasterID,
		EventType:   "CONTENT_FLAGGED",
		Title:       "Content review notice",
		Body:        "Some content from your stream is currently under review by our moderation team.",
		Channels:    []string{model.ChannelInApp, model.ChannelEmail},
		Priority:    model.PriorityRealtime,
		DedupeKey:   "CONTENT_FLAGGED:" + itemID,
		Metadata:    map[string]any{"itemId": itemID},
	}}
}

func (r *Router) onStreamSuspended(e map[string]any) []*model.NotificationRequest {
	broadcasterID := parseUUID(e["broadcasterId"])
	if broadcasterID == uuid.Nil {
		// Some events carry roomId; try to extract broadcasterId from nested data
		return nil
	}
	roomID, _ := e["roomId"].(string)
	return []*model.NotificationRequest{{
		RecipientID: broadcasterID,
		EventType:   "STREAM_SUSPENDED",
		Title:       "Your stream has been suspended",
		Body:        "Your stream was suspended due to a violation of our content policy. Please contact support.",
		Channels:    []string{model.ChannelPush, model.ChannelInApp, model.ChannelEmail},
		Priority:    model.PriorityCritical,
		DedupeKey:   "STREAM_SUSPENDED:" + roomID,
		Metadata:    map[string]any{"roomId": roomID},
	}}
}

func (r *Router) onCsamDetected(e map[string]any) []*model.NotificationRequest {
	// CSAM events go to the platform admin team, not the broadcaster.
	// In production, this would page the trust & safety on-call via PagerDuty.
	log.Error().
		Str("event_type", "CSAM_DETECTED").
		Interface("event", e).
		Msg("CSAM_DETECTED — escalate to Trust & Safety immediately")
	return nil
}

// ── Private-show handlers ─────────────────────────────────────────────────────

func (r *Router) onPrivateShowStarted(e map[string]any) []*model.NotificationRequest {
	broadcasterID := parseUUID(e["broadcasterId"])
	if broadcasterID == uuid.Nil {
		return nil
	}
	rate := formatTokens(e["ratePerMinute"])
	sessionID, _ := e["sessionId"].(string)
	return []*model.NotificationRequest{{
		RecipientID: broadcasterID,
		EventType:   "PRIVATE_SHOW_STARTED",
		Title:       "Private show started",
		Body:        fmt.Sprintf("A viewer has started a private show at %s tokens/min", rate),
		Channels:    []string{model.ChannelPush, model.ChannelInApp},
		Priority:    model.PriorityRealtime,
		DedupeKey:   "PRIVATE_SHOW_STARTED:" + sessionID,
		Metadata:    map[string]any{"sessionId": sessionID, "ratePerMinute": e["ratePerMinute"]},
	}}
}

func (r *Router) onPrivateShowEnded(e map[string]any) []*model.NotificationRequest {
	viewerID := parseUUID(e["viewerId"])
	sessionID, _ := e["sessionId"].(string)
	minutes := formatTokens(e["billedMinutes"])
	tokens := formatTokens(e["totalTokens"])

	if viewerID == uuid.Nil {
		return nil
	}
	return []*model.NotificationRequest{{
		RecipientID: viewerID,
		EventType:   "PRIVATE_SHOW_ENDED",
		Title:       "Private show ended",
		Body:        fmt.Sprintf("%s minutes — %s tokens charged", minutes, tokens),
		Channels:    []string{model.ChannelInApp},
		Priority:    model.PriorityRealtime,
		DedupeKey:   "PRIVATE_SHOW_ENDED:" + sessionID,
		Metadata:    map[string]any{"sessionId": sessionID, "billedMinutes": e["billedMinutes"], "totalTokens": e["totalTokens"]},
	}}
}

func (r *Router) onPrivateShowInsufficientFunds(e map[string]any) []*model.NotificationRequest {
	viewerID := parseUUID(e["viewerId"])
	if viewerID == uuid.Nil {
		return nil
	}
	sessionID, _ := e["sessionId"].(string)
	return []*model.NotificationRequest{{
		RecipientID: viewerID,
		EventType:   "PRIVATE_SHOW_INSUFFICIENT_FUNDS",
		Title:       "Insufficient tokens",
		Body:        "Your private show ended because you ran out of tokens. Top up to continue watching.",
		Channels:    []string{model.ChannelPush, model.ChannelInApp},
		Priority:    model.PriorityCritical,
		DedupeKey:   "PRIVATE_SHOW_FUNDS:" + sessionID,
		Metadata:    map[string]any{"sessionId": sessionID},
	}}
}

// ── User-profile handlers ─────────────────────────────────────────────────────

func (r *Router) onFollowedBroadcaster(ctx context.Context, e map[string]any) []*model.NotificationRequest {
	followerID := parseUUID(e["followerId"])
	followeeID := parseUUID(e["followeeId"])

	// Maintain the Redis follower set regardless of notification outcome.
	if followerID != uuid.Nil && followeeID != uuid.Nil {
		if err := r.redis.AddFollower(ctx, followeeID, followerID); err != nil {
			log.Warn().Err(err).Msg("add follower to Redis set")
		}
	}

	if followeeID == uuid.Nil {
		return nil
	}
	occurredAt, _ := e["occurredAt"].(string)
	return []*model.NotificationRequest{{
		RecipientID: followeeID,
		EventType:   "NEW_FOLLOWER",
		Title:       "New follower!",
		Body:        "Someone new is following you",
		Channels:    []string{model.ChannelInApp},
		Priority:    model.PriorityDigest,
		DedupeKey:   fmt.Sprintf("NEW_FOLLOWER:%s:%s", followeeID, occurredAt),
		Metadata:    map[string]any{"followerId": followerID.String()},
	}}
}

func (r *Router) onUnfollowedBroadcaster(ctx context.Context, e map[string]any) {
	followerID := parseUUID(e["followerId"])
	followeeID := parseUUID(e["followeeId"])
	if followerID == uuid.Nil || followeeID == uuid.Nil {
		return
	}
	if err := r.redis.RemoveFollower(ctx, followeeID, followerID); err != nil {
		log.Warn().Err(err).Msg("remove follower from Redis set")
	}
}

// ── helpers ───────────────────────────────────────────────────────────────────

func parseUUID(v any) uuid.UUID {
	s, ok := v.(string)
	if !ok {
		return uuid.Nil
	}
	uid, err := uuid.Parse(s)
	if err != nil {
		return uuid.Nil
	}
	return uid
}

func formatTokens(v any) string {
	switch t := v.(type) {
	case float64:
		return fmt.Sprintf("%.0f", t)
	case int64:
		return fmt.Sprintf("%d", t)
	case int:
		return fmt.Sprintf("%d", t)
	case string:
		return t
	}
	return "0"
}

// Compile-time use of time to avoid unused import errors in test scenarios.
var _ = time.Now
