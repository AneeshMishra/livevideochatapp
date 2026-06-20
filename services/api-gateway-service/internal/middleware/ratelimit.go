package middleware

import (
	"context"
	"fmt"
	"net"
	"net/http"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/rs/zerolog/log"
)

type RateLimiter struct {
	rdb          *redis.Client
	anonLimit    int // requests per minute for anonymous callers
	authedLimit  int // requests per minute for authenticated callers (after JWT header injected)
}

func NewRateLimiter(rdb *redis.Client, anonPerMin, authedPerMin int) *RateLimiter {
	return &RateLimiter{rdb: rdb, anonLimit: anonPerMin, authedLimit: authedPerMin}
}

// Limit is the middleware. It reads X-User-ID injected by the auth middleware (if present) and
// falls back to client IP for anonymous requests.
func (rl *RateLimiter) Limit(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		key, limit := rl.keyAndLimit(r)

		allowed, err := rl.check(r.Context(), key, limit)
		if err != nil {
			log.Warn().Err(err).Str("key", key).Msg("rate-limit redis error — allowing request")
			next.ServeHTTP(w, r)
			return
		}
		if !allowed {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusTooManyRequests)
			fmt.Fprintf(w, `{"error":"rate limit exceeded","retry_after_seconds":60}`)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func (rl *RateLimiter) keyAndLimit(r *http.Request) (string, int) {
	if uid := r.Header.Get("X-User-ID"); uid != "" {
		return "rl:user:" + uid, rl.authedLimit
	}
	ip, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		ip = r.RemoteAddr
	}
	// Prefer X-Forwarded-For when the gateway is behind a load-balancer.
	if forwarded := r.Header.Get("X-Forwarded-For"); forwarded != "" {
		ip = strings.Split(forwarded, ",")[0]
	}
	return "rl:ip:" + ip, rl.anonLimit
}

// check uses a sliding 60-second window implemented with Redis INCR + EXPIRE.
func (rl *RateLimiter) check(ctx context.Context, key string, limit int) (bool, error) {
	pipe := rl.rdb.TxPipeline()
	incr := pipe.Incr(ctx, key)
	pipe.Expire(ctx, key, time.Minute)
	if _, err := pipe.Exec(ctx); err != nil {
		return false, err
	}
	return incr.Val() <= int64(limit), nil
}
