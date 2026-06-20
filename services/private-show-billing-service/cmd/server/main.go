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

	"github.com/platform/private-show-billing-service/internal/auth"
	"github.com/platform/private-show-billing-service/internal/billing"
	"github.com/platform/private-show-billing-service/internal/client"
	"github.com/platform/private-show-billing-service/internal/config"
	"github.com/platform/private-show-billing-service/internal/db"
	"github.com/platform/private-show-billing-service/internal/handler"
	kafkapkg "github.com/platform/private-show-billing-service/internal/kafka"
	redisstore "github.com/platform/private-show-billing-service/internal/redis"
)

func main() {
	cfg := config.Load()

	level, _ := zerolog.ParseLevel(cfg.LogLevel)
	zerolog.SetGlobalLevel(level)
	log.Logger = log.Output(zerolog.ConsoleWriter{Out: os.Stderr, TimeFormat: time.RFC3339})

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	// ── PostgreSQL ────────────────────────────────────────────────────────────
	postgres, err := db.NewPostgres(ctx, cfg.DatabaseURL)
	if err != nil {
		log.Fatal().Err(err).Msg("postgres init failed")
	}
	defer postgres.Close()

	if err := postgres.RunMigrations(ctx); err != nil {
		log.Fatal().Err(err).Msg("migrations failed")
	}

	// ── Redis ─────────────────────────────────────────────────────────────────
	rdbOpts, err := redis.ParseURL(cfg.RedisURL)
	if err != nil {
		log.Fatal().Err(err).Msg("invalid REDIS_URL")
	}
	rdb := redis.NewClient(rdbOpts)
	defer rdb.Close()
	store := redisstore.NewStore(rdb)

	// ── Kafka producer ────────────────────────────────────────────────────────
	producer := kafkapkg.NewProducer(cfg.KafkaBrokers, cfg.KafkaPrivateTopic)
	defer producer.Close()

	// ── Wallet client ─────────────────────────────────────────────────────────
	walletClient, err := client.NewWalletClient(cfg.WalletServiceURL, cfg.JWTSecret)
	if err != nil {
		log.Fatal().Err(err).Msg("wallet client init failed")
	}

	// ── Core service ──────────────────────────────────────────────────────────
	svc := billing.NewService(postgres, store, walletClient, producer)

	// ── Kafka consumer (streaming events → auto-end sessions on STREAM_ENDED) ─
	consumer := kafkapkg.NewConsumer(
		cfg.KafkaBrokers,
		cfg.KafkaStreamTopic,
		cfg.KafkaGroupID,
		svc.HandleStreamEnded,
	)
	defer consumer.Close()

	// ── Background billing meter ──────────────────────────────────────────────
	meter := billing.NewMeter(svc, store, cfg.BillingIntervalSec)

	// ── HTTP server ───────────────────────────────────────────────────────────
	jwtValidator, err := auth.NewValidator(cfg.JWTSecret)
	if err != nil {
		log.Fatal().Err(err).Msg("jwt validator init failed")
	}
	h := handler.New(svc, postgres, jwtValidator)

	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      h.Routes(),
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	g, gctx := errgroup.WithContext(ctx)

	g.Go(func() error {
		log.Info().Str("port", cfg.Port).Msg("private-show-billing-service listening")
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			return err
		}
		return nil
	})

	g.Go(func() error {
		return meter.Run(gctx)
	})

	g.Go(func() error {
		return consumer.Run(gctx)
	})

	g.Go(func() error {
		<-gctx.Done()
		log.Info().Msg("shutting down private-show-billing-service")
		shutCtx, shutCancel := context.WithTimeout(context.Background(), 15*time.Second)
		defer shutCancel()
		return srv.Shutdown(shutCtx)
	})

	if err := g.Wait(); err != nil {
		log.Error().Err(err).Msg("private-show-billing-service exited with error")
		os.Exit(1)
	}
}
