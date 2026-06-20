package main

import (
	"context"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"golang.org/x/sync/errgroup"

	"github.com/platform/api-gateway-service/internal/auth"
	"github.com/platform/api-gateway-service/internal/config"
	"github.com/platform/api-gateway-service/internal/gateway"
	"github.com/platform/api-gateway-service/internal/middleware"
)

func main() {
	cfg := config.Load()

	level, _ := zerolog.ParseLevel(cfg.LogLevel)
	zerolog.SetGlobalLevel(level)
	log.Logger = log.Output(zerolog.ConsoleWriter{Out: os.Stderr, TimeFormat: time.RFC3339})

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	// ── Redis (rate limiting) ─────────────────────────────────────────────────
	rdbOpts, err := redis.ParseURL(cfg.RedisURL)
	if err != nil {
		log.Fatal().Err(err).Msg("invalid REDIS_URL")
	}
	rdb := redis.NewClient(rdbOpts)
	defer rdb.Close()

	if err := rdb.Ping(ctx).Err(); err != nil {
		log.Fatal().Err(err).Msg("redis ping failed")
	}

	// ── JWT validator ─────────────────────────────────────────────────────────
	jwtValidator, err := auth.NewValidator(cfg.JWTSecret)
	if err != nil {
		log.Fatal().Err(err).Msg("jwt validator init failed")
	}

	// ── Rate limiter ──────────────────────────────────────────────────────────
	rateLimiter := middleware.NewRateLimiter(
		rdb,
		cfg.RateLimitAnonymousPerMin,
		cfg.RateLimitAuthenticatedPerMin,
	)

	// ── Gateway router ────────────────────────────────────────────────────────
	gw := gateway.New(cfg, rateLimiter, jwtValidator)

	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      gw.Handler(),
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 60 * time.Second, // longer for WebSocket upgrades
		IdleTimeout:  120 * time.Second,
	}

	g, gctx := errgroup.WithContext(ctx)

	g.Go(func() error {
		log.Info().
			Str("port", cfg.Port).
			Int("anon_rpm", cfg.RateLimitAnonymousPerMin).
			Int("auth_rpm", cfg.RateLimitAuthenticatedPerMin).
			Msg("api-gateway-service listening")
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			return err
		}
		return nil
	})

	g.Go(func() error {
		<-gctx.Done()
		log.Info().Msg("shutting down api-gateway-service")
		shutCtx, shutCancel := context.WithTimeout(context.Background(), 15*time.Second)
		defer shutCancel()
		return srv.Shutdown(shutCtx)
	})

	if err := g.Wait(); err != nil {
		log.Error().Err(err).Msg("api-gateway-service exited with error")
		os.Exit(1)
	}
}
