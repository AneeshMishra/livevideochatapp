package main

import (
	"context"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"golang.org/x/sync/errgroup"

	"github.com/platform/chat-service/internal/auth"
	"github.com/platform/chat-service/internal/config"
	"github.com/platform/chat-service/internal/handler"
	"github.com/platform/chat-service/internal/kafka"
	"github.com/platform/chat-service/internal/pubsub"
	"github.com/platform/chat-service/internal/ratelimit"
	"github.com/platform/chat-service/internal/store"
	"github.com/platform/chat-service/internal/ws"
)

func main() {
	cfg := config.Load()

	level, _ := zerolog.ParseLevel(cfg.LogLevel)
	zerolog.SetGlobalLevel(level)
	log.Logger = log.Output(zerolog.ConsoleWriter{Out: os.Stderr, TimeFormat: time.RFC3339})

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	// ── Redis ─────────────────────────────────────────────────────────────────
	psClient, err := pubsub.NewClient(cfg.RedisURL)
	if err != nil {
		log.Fatal().Err(err).Msg("redis connect failed")
	}
	defer psClient.Close()

	// ── ScyllaDB ──────────────────────────────────────────────────────────────
	scyllaStore, err := store.NewScyllaStore(cfg.ScyllaHosts, cfg.ScyllaKeyspace)
	if err != nil {
		log.Fatal().Err(err).Msg("scylladb connect failed")
	}
	defer scyllaStore.Close()

	// ── Auth ──────────────────────────────────────────────────────────────────
	jwtValidator, err := auth.NewValidator(cfg.JWTSecret)
	if err != nil {
		log.Fatal().Err(err).Msg("jwt validator init failed")
	}

	// ── Core components ───────────────────────────────────────────────────────
	rl := ratelimit.New(psClient.RedisClient(), cfg.RateLimitMessages, cfg.RateLimitWindowSec)
	hub := ws.NewHub(psClient, scyllaStore, rl)
	h := handler.New(hub, scyllaStore, jwtValidator, cfg)

	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      h.Routes(),
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 0, // WebSocket connections are long-lived
		IdleTimeout:  120 * time.Second,
	}

	g, gctx := errgroup.WithContext(ctx)

	// HTTP server (WebSocket + REST history endpoint)
	g.Go(func() error {
		log.Info().Str("port", cfg.Port).Msg("chat-service listening")
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			return err
		}
		return nil
	})

	// Hub: Redis pub/sub subscriber — fans out cross-node messages to local clients
	g.Go(func() error {
		return hub.Run(gctx)
	})

	// Kafka consumer: forwards tipping events (TIP_RECEIVED, goal updates) to rooms
	g.Go(func() error {
		consumer := kafka.NewConsumer(
			cfg.KafkaBrokers,
			cfg.KafkaTippingTopic,
			cfg.KafkaGroupID,
			hub,
		)
		return consumer.Run(gctx)
	})

	// Graceful shutdown
	g.Go(func() error {
		<-gctx.Done()
		log.Info().Msg("shutting down chat-service")
		shutCtx, shutCancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer shutCancel()
		return srv.Shutdown(shutCtx)
	})

	if err := g.Wait(); err != nil {
		log.Error().Err(err).Msg("chat-service exited with error")
		os.Exit(1)
	}
}
