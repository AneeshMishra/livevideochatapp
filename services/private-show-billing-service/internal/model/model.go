package model

import (
	"time"

	"github.com/google/uuid"
)

type SessionStatus string
type ShowType string
type EndReason string

const (
	StatusActive    SessionStatus = "ACTIVE"
	StatusPaused    SessionStatus = "PAUSED"
	StatusCompleted SessionStatus = "COMPLETED"

	ShowTypePrivate ShowType = "PRIVATE"
	ShowTypeSpy     ShowType = "SPY"
	ShowTypeGroup   ShowType = "GROUP"

	EndReasonViewerEnded       EndReason = "VIEWER_ENDED"
	EndReasonBroadcasterEnded  EndReason = "BROADCASTER_ENDED"
	EndReasonInsufficientFunds EndReason = "INSUFFICIENT_FUNDS"
	EndReasonStreamEnded       EndReason = "STREAM_ENDED"
)

type Session struct {
	ID            uuid.UUID
	ViewerID      uuid.UUID
	BroadcasterID uuid.UUID
	RoomID        uuid.UUID
	ShowType      ShowType
	Status        SessionStatus
	RatePerMinute int64
	BilledMinutes int
	TotalTokens   int64
	StartedAt     time.Time
	EndedAt       *time.Time
	EndReason     *EndReason
	CreatedAt     time.Time
}

type BillingTick struct {
	ID            uuid.UUID
	SessionID     uuid.UUID
	ViewerID      uuid.UUID
	BroadcasterID uuid.UUID
	TokensCharged int64
	MinuteNumber  int
	WalletTxID    *uuid.UUID
	BilledAt      time.Time
}
