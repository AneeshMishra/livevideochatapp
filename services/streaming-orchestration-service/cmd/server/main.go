package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"golang.org/x/sync/errgroup"

	"github.com/platform/streaming-orchestration-service/internal/auth"
	"github.com/platform/streaming-orchestration-service/internal/config"
	"github.com/platform/streaming-orchestration-service/internal/handler"
	kafkapkg "github.com/platform/streaming-orchestration-service/internal/kafka"
	"github.com/platform/streaming-orchestration-service/internal/provider"
	"github.com/platform/streaming-orchestration-service/internal/service"
	"github.com/platform/streaming-orchestration-service/internal/store"
)

func main() {
	cfg := config.Load()

	level, _ := zerolog.ParseLevel(cfg.LogLevel)
	zerolog.SetGlobalLevel(level)
	log.Logger = log.Output(zerolog.ConsoleWriter{Out: os.Stderr, TimeFormat: time.RFC3339})

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	// ── PostgreSQL ────────────────────────────────────────────────────────────
	db, err := store.NewDB(ctx, cfg.DBURL)
	if err != nil {
		log.Fatal().Err(err).Msg("postgres connect failed")
	}
	defer db.Close()

	// ── Redis ─────────────────────────────────────────────────────────────────
	redisStore, err := store.NewRedisStore(cfg.RedisURL)
	if err != nil {
		log.Fatal().Err(err).Msg("redis connect failed")
	}
	defer redisStore.Close()

	// ── Streaming provider ────────────────────────────────────────────────────
	sp, err := buildProvider(ctx, cfg)
	if err != nil {
		log.Fatal().Err(err).Msg("streaming provider init failed")
	}

	// ── Kafka producer ────────────────────────────────────────────────────────
	producer := kafkapkg.NewProducer(cfg.KafkaBrokers, cfg.KafkaStreamingTopic)
	defer producer.Close()

	// ── Auth ──────────────────────────────────────────────────────────────────
	jwtValidator, err := auth.NewValidator(cfg.JWTSecret)
	if err != nil {
		log.Fatal().Err(err).Msg("jwt validator init failed")
	}

	// ── Orchestrator ──────────────────────────────────────────────────────────
	orchestrator := service.NewOrchestrator(db, redisStore, sp, producer, cfg.PromotionThreshold)

	// ── HTTP server ───────────────────────────────────────────────────────────
	h := handler.New(orchestrator, jwtValidator)
	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      h.Routes(),
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 10 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// ── Kafka presence consumer ────────────────────────────────────────────────
	presenceConsumer := kafkapkg.NewPresenceConsumer(
		cfg.KafkaBrokers,
		cfg.KafkaPresenceTopic,
		cfg.KafkaGroupID,
		orchestrator,
	)

	g, gctx := errgroup.WithContext(ctx)

	g.Go(func() error {
		log.Info().
			Str("port", cfg.Port).
			Str("provider", cfg.StreamingProvider).
			Int64("promotion_threshold", cfg.PromotionThreshold).
			Msg("streaming-orchestration-service starting")
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			return err
		}
		return nil
	})

	// Consume presence.events to drive WebRTC → LL-HLS promotion logic.
	g.Go(func() error {
		return presenceConsumer.Run(gctx)
	})

	// Graceful shutdown.
	g.Go(func() error {
		<-gctx.Done()
		log.Info().Msg("shutting down streaming-orchestration-service")
		shutCtx, shutCancel := context.WithTimeout(context.Background(), 15*time.Second)
		defer shutCancel()
		return srv.Shutdown(shutCtx)
	})

	if err := g.Wait(); err != nil {
		log.Error().Err(err).Msg("streaming-orchestration-service exited with error")
		os.Exit(1)
	}
}

// buildProvider instantiates the correct streaming provider based on config.
func buildProvider(ctx context.Context, cfg *config.Config) (provider.StreamingProvider, error) {
	switch cfg.StreamingProvider {
	case "IVS":
		log.Info().Str("region", cfg.AWSRegion).Msg("using Amazon IVS provider")
		return provider.NewIVSProvider(ctx, cfg.AWSRegion)
	case "MOCK":
		log.Info().Msg("using Mock streaming provider (dev mode)")
		return provider.NewMockProvider(), nil
	default:
		return nil, fmt.Errorf("unknown STREAMING_PROVIDER=%q; supported: IVS, MOCK", cfg.StreamingProvider)
	}
}
