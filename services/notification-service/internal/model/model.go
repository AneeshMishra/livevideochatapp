package model

import (
	"time"

	"github.com/google/uuid"
)

// Channel identifiers
const (
	ChannelEmail    = "EMAIL"
	ChannelPush     = "PUSH"
	ChannelInApp    = "INAPP"
	ChannelWhatsApp = "WHATSAPP"
)

// Priority tiers (CRITICAL bypasses quiet hours and rate limits)
const (
	PriorityCritical = "CRITICAL"
	PriorityRealtime = "REALTIME"
	PriorityDigest   = "DIGEST"
)

// Delivery outcomes
const (
	StatusSent        = "SENT"
	StatusFailed      = "FAILED"
	StatusSkipped     = "SKIPPED"
	StatusRateLimited = "RATE_LIMITED"
	StatusDeduped     = "DEDUPED"
)

// NotificationRequest is built by the router for each domain event.
type NotificationRequest struct {
	RecipientID uuid.UUID
	EventType   string
	Title       string
	Body        string
	Channels    []string
	Priority    string
	DedupeKey   string         // "{eventType}:{sourceID}" — prevents resend on Kafka re-delivery
	Metadata    map[string]any // passed through to push/email adapters as extra data
}

// NotificationLog is persisted for every send attempt.
type NotificationLog struct {
	ID        uuid.UUID
	UserID    uuid.UUID
	EventType string
	Channel   string
	Status    string
	Title     string
	Body      string
	Metadata  string
	Error     string
	CreatedAt time.Time
}

// UserPreference controls which channels a user wants for each event type.
type UserPreference struct {
	ID        uuid.UUID
	UserID    uuid.UUID
	EventType string
	Channel   string
	Enabled   bool
	CreatedAt time.Time
	UpdatedAt time.Time
}

// DeviceToken stores FCM / APNs registration tokens per user.
type DeviceToken struct {
	ID        uuid.UUID
	UserID    uuid.UUID
	Token     string
	Platform  string // FCM | APNS
	Active    bool
	CreatedAt time.Time
}
