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

	"github.com/platform/presence-service/internal/auth"
	"github.com/platform/presence-service/internal/config"
	"github.com/platform/presence-service/internal/handler"
	kafkapkg "github.com/platform/presence-service/internal/kafka"
	"github.com/platform/presence-service/internal/presence"
)

func main() {
	cfg := config.Load()

	level, _ := zerolog.ParseLevel(cfg.LogLevel)
	zerolog.SetGlobalLevel(level)
	log.Logger = log.Output(zerolog.ConsoleWriter{Out: os.Stderr, TimeFormat: time.RFC3339})

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	// ── Redis ─────────────────────────────────────────────────────────────────
	opts, err := redis.ParseURL(cfg.RedisURL)
	if err != nil {
		log.Fatal().Err(err).Msg("invalid REDIS_URL")
	}
	rdb := redis.NewClient(opts)
	defer rdb.Close()

	// ── Core components ───────────────────────────────────────────────────────
	store := presence.NewStore(rdb, cfg.HeartbeatTTLSec)
	producer := kafkapkg.NewProducer(cfg.KafkaBrokers, cfg.KafkaPresenceTopic)
	defer producer.Close()

	jwtValidator, err := auth.NewValidator(cfg.JWTSecret)
	if err != nil {
		log.Fatal().Err(err).Msg("jwt validator init failed")
	}

	h := handler.New(store, producer, jwtValidator)

	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      h.Routes(),
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 10 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	g, gctx := errgroup.WithContext(ctx)

	// HTTP server
	g.Go(func() error {
		log.Info().Str("port", cfg.Port).Msg("presence-service listening")
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			return err
		}
		return nil
	})

	// Background sweeper: periodically publishes ROOM_COUNT_SNAPSHOT events
	// so the catalog/discovery service can update its viewer-count cache.
	g.Go(func() error {
		return runSweeper(gctx, store, producer, time.Duration(cfg.SweeperIntervalSec)*time.Second)
	})

	// Graceful shutdown
	g.Go(func() error {
		<-gctx.Done()
		log.Info().Msg("shutting down presence-service")
		shutCtx, shutCancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer shutCancel()
		return srv.Shutdown(shutCtx)
	})

	if err := g.Wait(); err != nil {
		log.Error().Err(err).Msg("presence-service exited with error")
		os.Exit(1)
	}
}

// runSweeper periodically computes viewer counts for all recently-active rooms
// and emits ROOM_COUNT_SNAPSHOT events to the presence.events Kafka topic.
// Consumers (e.g., catalog service) use these to refresh their cached counts.
func runSweeper(ctx context.Context, store *presence.Store, producer *kafkapkg.Producer, interval time.Duration) error {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	logger := log.With().Str("component", "sweeper").Logger()

	for {
		select {
		case <-ctx.Done():
			return nil
		case <-ticker.C:
			// Look at rooms active in the last 10 minutes.
			rooms, err := store.ActiveRooms(ctx, 10*time.Minute)
			if err != nil {
				logger.Error().Err(err).Msg("fetch active rooms")
				continue
			}
			if len(rooms) == 0 {
				continue
			}

			counts, err := store.RoomViewerCounts(ctx, rooms)
			if err != nil {
				logger.Error().Err(err).Msg("fetch room counts")
				continue
			}

			for roomID, count := range counts {
				producer.PublishRoomCount(ctx, roomID, count)
			}
			logger.Debug().Int("rooms", len(rooms)).Msg("room count snapshots published")
		}
	}
}
