package ws

import "sync"

// Room tracks all live WebSocket clients subscribed to a single broadcast room.
type Room struct {
	id      string
	mu      sync.RWMutex
	clients map[*Client]struct{}
}

func newRoom(id string) *Room {
	return &Room{
		id:      id,
		clients: make(map[*Client]struct{}),
	}
}

func (r *Room) add(c *Client) {
	r.mu.Lock()
	r.clients[c] = struct{}{}
	r.mu.Unlock()
}

func (r *Room) remove(c *Client) {
	r.mu.Lock()
	delete(r.clients, c)
	r.mu.Unlock()
}

func (r *Room) size() int {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return len(r.clients)
}

// broadcast pushes msg to every client in the room concurrently-safe.
// A client whose send buffer is full is disconnected immediately to prevent
// head-of-line blocking on a 10k-viewer room.
func (r *Room) broadcast(msg []byte) {
	r.mu.Lock()
	defer r.mu.Unlock()
	for c := range r.clients {
		if !c.trySend(msg) {
			close(c.send)
			delete(r.clients, c)
			c.logger.Warn().Msg("slow client disconnected: send buffer full")
		}
	}
}
