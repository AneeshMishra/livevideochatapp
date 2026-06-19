package ws

import (
	"encoding/json"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"github.com/rs/zerolog"

	"github.com/platform/chat-service/internal/message"
)

const (
	writeWait      = 10 * time.Second
	pongWait       = 60 * time.Second
	pingPeriod     = 50 * time.Second // must be < pongWait
	sendBufferSize = 256              // per-client outbound queue depth
)

// Client represents a single authenticated WebSocket connection.
type Client struct {
	id       uuid.UUID
	userID   uuid.UUID
	username string
	roles    []string
	roomID   string
	conn     *websocket.Conn
	send     chan []byte
	hub      *Hub
	logger   zerolog.Logger
}

func NewClient(
	conn *websocket.Conn,
	userID uuid.UUID,
	username string,
	roles []string,
	roomID string,
	hub *Hub,
	logger zerolog.Logger,
) *Client {
	return &Client{
		id:       uuid.New(),
		userID:   userID,
		username: username,
		roles:    roles,
		roomID:   roomID,
		conn:     conn,
		send:     make(chan []byte, sendBufferSize),
		hub:      hub,
		logger:   logger,
	}
}

// SendWelcome pushes a SYSTEM greeting to the client immediately after join.
func (c *Client) SendWelcome() {
	msg, _ := json.Marshal(&message.OutboundMessage{
		Type:      message.TypeSystem,
		RoomID:    c.roomID,
		Content:   "Welcome! You are now connected to the chat.",
		Timestamp: time.Now().UTC(),
	})
	c.trySend(msg)
}

// WritePump flushes the outbound queue to the WebSocket; runs in its own goroutine.
func (c *Client) WritePump() {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		c.conn.Close()
	}()

	for {
		select {
		case msg, ok := <-c.send:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				// Hub closed the channel — send a clean WebSocket close frame.
				c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}
			w, err := c.conn.NextWriter(websocket.TextMessage)
			if err != nil {
				return
			}
			w.Write(msg)
			// Drain any buffered messages in a single write for efficiency.
			n := len(c.send)
			for i := 0; i < n; i++ {
				w.Write([]byte{'\n'})
				w.Write(<-c.send)
			}
			if err := w.Close(); err != nil {
				return
			}
		case <-ticker.C:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

// ReadPump reads frames from the WebSocket and dispatches to the hub; runs in its own goroutine.
func (c *Client) ReadPump(maxMsgBytes int64) {
	defer func() {
		c.hub.unregister(c)
		c.conn.Close()
	}()

	c.conn.SetReadLimit(maxMsgBytes)
	c.conn.SetReadDeadline(time.Now().Add(pongWait))
	c.conn.SetPongHandler(func(string) error {
		c.conn.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})

	for {
		_, raw, err := c.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				c.logger.Warn().Err(err).Msg("websocket closed unexpectedly")
			}
			return
		}
		c.hub.processInbound(c, raw)
	}
}

// trySend attempts a non-blocking write to the client's send channel.
// Returns false if the buffer is full (caller should disconnect the client).
func (c *Client) trySend(msg []byte) bool {
	select {
	case c.send <- msg:
		return true
	default:
		return false
	}
}
