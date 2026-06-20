package gateway

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"github.com/rs/zerolog/log"

	"github.com/platform/api-gateway-service/internal/auth"
	"github.com/platform/api-gateway-service/internal/bff"
	"github.com/platform/api-gateway-service/internal/config"
	"github.com/platform/api-gateway-service/internal/middleware"
	"github.com/platform/api-gateway-service/internal/proxy"
)

// route associates a path prefix with an upstream proxy handler.
// requireAuth=true means the gateway enforces JWT presence before forwarding.
type route struct {
	prefix      string
	handler     http.Handler
	requireAuth bool
}

// Gateway is the HTTP handler that routes, validates, and proxies all requests.
type Gateway struct {
	routes  []route
	rl      *middleware.RateLimiter
	jwt     *auth.Validator
	bffHnd  *bff.Handler
	cors    func(http.Handler) http.Handler
}

func New(cfg *config.Config, rl *middleware.RateLimiter, jwtV *auth.Validator) *Gateway {
	bffH := bff.NewHandler(
		cfg.CatalogURL, cfg.PresenceURL, cfg.StreamingURL, cfg.BroadcasterURL)

	g := &Gateway{
		rl:     rl,
		jwt:    jwtV,
		bffHnd: bffH,
		cors:   middleware.CORS(cfg.AllowedOrigins),
	}

	// Route table — order matters: more specific prefixes must come first.
	// requireAuth is advisory at the gateway layer; each service re-validates.
	// Protected routes reject the request immediately if no valid JWT is found.
	g.routes = []route{
		// ── Auth (always public) ─────────────────────────────────────────────
		{"/api/v1/auth/", proxy.New(cfg.IdentityAuthURL), false},

		// ── User Profile ────────────────────────────────────────────────────
		{"/api/v1/profiles/", proxy.New(cfg.UserProfileURL), true},

		// ── Broadcaster (profile read is public; write is protected) ─────────
		{"/api/v1/broadcasters/", proxy.New(cfg.BroadcasterURL), false},

		// ── Wallet ──────────────────────────────────────────────────────────
		{"/api/v1/wallet/", proxy.New(cfg.WalletURL), true},

		// ── Payments ────────────────────────────────────────────────────────
		{"/api/v1/payments/", proxy.New(cfg.PaymentsURL), true},

		// ── Tips & Gifts ─────────────────────────────────────────────────────
		{"/api/v1/tips/", proxy.New(cfg.TippingURL), false},    // leaderboard is public
		{"/api/v1/gifts/", proxy.New(cfg.TippingURL), false},   // catalog is public

		// ── Chat (WebSocket) ─────────────────────────────────────────────────
		{"/api/v1/chat/", proxy.New(cfg.ChatURL), false},       // auth inside service

		// ── Presence ────────────────────────────────────────────────────────
		{"/api/v1/presence/", proxy.New(cfg.PresenceURL), false},

		// ── Streaming Orchestration ──────────────────────────────────────────
		{"/api/v1/streaming/", proxy.New(cfg.StreamingURL), false},

		// ── Catalog / Discovery ──────────────────────────────────────────────
		{"/api/v1/catalog/", proxy.New(cfg.CatalogURL), false},

		// ── Moderation ──────────────────────────────────────────────────────
		{"/api/v1/moderation/", proxy.New(cfg.ModerationURL), true},

		// ── Private Shows ────────────────────────────────────────────────────
		{"/api/v1/private-shows/", proxy.New(cfg.PrivateShowURL), true},

		// ── Notifications ────────────────────────────────────────────────────
		{"/api/v1/notifications/", proxy.New(cfg.NotificationURL), true},
	}

	return g
}

// Handler returns the root http.Handler with all middleware applied.
func (g *Gateway) Handler() http.Handler {
	mux := http.NewServeMux()

	// BFF aggregation endpoints (no auth required — downstream data is public).
	mux.HandleFunc("GET /api/v1/bff/home", g.bffHnd.Home)
	mux.HandleFunc("GET /api/v1/bff/room/{roomId}", g.bffHnd.Room)

	// Health / readiness.
	mux.HandleFunc("GET /health", g.health)
	mux.HandleFunc("GET /ready", g.ready)

	// Catch-all: match route table then proxy.
	mux.HandleFunc("/", g.dispatch)

	// Stack: CORS → Logger → RateLimit → mux
	var h http.Handler = mux
	h = g.rl.Limit(h)
	h = middleware.Logger(h)
	h = g.cors(h)
	return h
}

// dispatch resolves the request to a route entry, optionally enforces JWT,
// injects user-context headers, then forwards to the upstream proxy.
func (g *Gateway) dispatch(w http.ResponseWriter, r *http.Request) {
	for _, rt := range g.routes {
		if strings.HasPrefix(r.URL.Path, rt.prefix) {
			// Try JWT extraction regardless of requireAuth so we can inject
			// X-User-ID for rate-limiting and downstream logging.
			claims := g.tryExtractClaims(r)

			if rt.requireAuth && claims == nil {
				jsonErr(w, "authentication required", http.StatusUnauthorized)
				return
			}

			if claims != nil {
				// Inject trusted user context headers for downstream services.
				r.Header.Set("X-User-ID", claims.UserID.String())
				r.Header.Set("X-User-Roles", strings.Join(claims.Roles, ","))
				r.Header.Set("X-Gateway-Validated", "true")
				// Do NOT expose internal validation marker to clients.
			} else {
				r.Header.Del("X-User-ID")
				r.Header.Del("X-User-Roles")
				r.Header.Del("X-Gateway-Validated")
			}

			rt.handler.ServeHTTP(w, r)
			return
		}
	}

	jsonErr(w, fmt.Sprintf("no route for path: %s", r.URL.Path), http.StatusNotFound)
}

// tryExtractClaims parses the Authorization header. Returns nil on any error.
func (g *Gateway) tryExtractClaims(r *http.Request) *auth.Claims {
	tok := r.Header.Get("Authorization")
	if strings.HasPrefix(tok, "Bearer ") {
		tok = tok[7:]
	}
	if tok == "" {
		return nil
	}
	claims, err := g.jwt.Validate(tok)
	if err != nil {
		log.Debug().Err(err).Msg("jwt validation failed at gateway")
		return nil
	}
	return claims
}

func (g *Gateway) health(w http.ResponseWriter, _ *http.Request) {
	jsonOK(w, map[string]string{"status": "UP"})
}

func (g *Gateway) ready(w http.ResponseWriter, _ *http.Request) {
	jsonOK(w, map[string]string{"status": "READY"})
}

func jsonOK(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}

func jsonErr(w http.ResponseWriter, msg string, code int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(map[string]string{"error": msg})
}
