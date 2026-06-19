package message

import (
	"time"

	"github.com/google/uuid"
)

// Inbound types (client → server).
const (
	TypeInboundChat = "CHAT_MESSAGE"
	TypePing        = "PING"
)

// Outbound types (server → client).
const (
	TypeChatMessage     = "CHAT_MESSAGE"
	TypeTipNotification = "TIP_NOTIFICATION"
	TypeGoalUpdate      = "GOAL_UPDATE"
	TypeGoalCompleted   = "GOAL_COMPLETED"
	TypeSystem          = "SYSTEM"
	TypeError           = "ERROR"
	TypePong            = "PONG"
)

// InboundMessage is a JSON frame received from a WebSocket client.
type InboundMessage struct {
	Type    string `json:"type"`
	RoomID  string `json:"roomId"`
	Content string `json:"content,omitempty"`
}

// OutboundMessage is a JSON frame broadcast to WebSocket clients.
type OutboundMessage struct {
	Type        string    `json:"type"`
	MessageID   string    `json:"messageId,omitempty"`
	RoomID      string    `json:"roomId"`
	UserID      string    `json:"userId,omitempty"`
	Username    string    `json:"username,omitempty"`
	Content     string    `json:"content,omitempty"`
	TokenAmount int64     `json:"tokenAmount,omitempty"`
	GoalTitle   string    `json:"goalTitle,omitempty"`
	Progress    int       `json:"progress,omitempty"`
	Timestamp   time.Time `json:"timestamp"`
}

// ChatRecord is persisted to ScyllaDB.
type ChatRecord struct {
	RoomID     uuid.UUID
	DateBucket string // YYYY-MM-DD partition bucket
	CreatedAt  time.Time
	MessageID  uuid.UUID
	UserID     uuid.UUID
	Username   string
	Content    string
	MsgType    string
}
