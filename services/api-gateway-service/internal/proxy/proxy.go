package proxy

import (
	"io"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"strings"
	"time"

	"github.com/rs/zerolog/log"
)

// New returns a reverse-proxy handler for the given target URL.
// WebSocket upgrade requests are tunnelled via a raw TCP connection so that
// the Upgrade/Connection hop-by-hop headers are preserved end-to-end.
func New(targetRaw string) http.Handler {
	target, err := url.Parse(targetRaw)
	if err != nil {
		panic("invalid proxy target: " + targetRaw)
	}

	rp := httputil.NewSingleHostReverseProxy(target)

	// Custom director: set Host header to the backend host and strip
	// internal-only request headers so the backend sees a clean request.
	rp.Director = func(req *http.Request) {
		req.URL.Scheme = target.Scheme
		req.URL.Host = target.Host
		req.Host = target.Host

		// Remove headers that must not be forwarded downstream.
		req.Header.Del("X-Forwarded-Proto")
		req.Header.Del("X-Forwarded-Port")
	}

	rp.ErrorHandler = func(w http.ResponseWriter, r *http.Request, err error) {
		log.Error().Err(err).Str("target", targetRaw).Str("path", r.URL.Path).Msg("proxy error")
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadGateway)
		w.Write([]byte(`{"error":"upstream service unavailable"}`))
	}

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if isWebSocketUpgrade(r) {
			serveWebSocket(w, r, target)
			return
		}
		rp.ServeHTTP(w, r)
	})
}

func isWebSocketUpgrade(r *http.Request) bool {
	return strings.EqualFold(r.Header.Get("Upgrade"), "websocket") &&
		strings.Contains(strings.ToLower(r.Header.Get("Connection")), "upgrade")
}

// serveWebSocket opens a raw TCP connection to the backend and performs a
// bidirectional copy so that the WebSocket handshake and subsequent frames
// pass through unmodified.
func serveWebSocket(w http.ResponseWriter, r *http.Request, target *url.URL) {
	backendAddr := target.Host
	if !strings.Contains(backendAddr, ":") {
		if target.Scheme == "https" {
			backendAddr += ":443"
		} else {
			backendAddr += ":80"
		}
	}

	backendConn, err := net.DialTimeout("tcp", backendAddr, 10*time.Second)
	if err != nil {
		log.Error().Err(err).Str("backend", backendAddr).Msg("websocket dial failed")
		http.Error(w, "bad gateway", http.StatusBadGateway)
		return
	}
	defer backendConn.Close()

	// Rewrite request to target backend path.
	r.URL.Host = target.Host
	r.URL.Scheme = target.Scheme
	r.Host = target.Host

	// Forward the raw HTTP upgrade request to the backend.
	if err := r.Write(backendConn); err != nil {
		log.Error().Err(err).Msg("websocket request write failed")
		return
	}

	// Hijack the client connection.
	hijacker, ok := w.(http.Hijacker)
	if !ok {
		log.Error().Msg("websocket hijack not supported by ResponseWriter")
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}
	clientConn, _, err := hijacker.Hijack()
	if err != nil {
		log.Error().Err(err).Msg("websocket hijack failed")
		return
	}
	defer clientConn.Close()

	// Bidirectional tunnel.
	done := make(chan struct{}, 2)
	go func() { io.Copy(backendConn, clientConn); done <- struct{}{} }()
	go func() { io.Copy(clientConn, backendConn); done <- struct{}{} }()
	<-done
}
