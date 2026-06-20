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

	"github.com/platform/notification-service/internal/auth"
	"github.com/platform/notification-service/internal/channel"
	"github.com/platform/notification-service/internal/client"
	"github.com/platform/notification-service/internal/config"
	"github.com/platform/notification-service/internal/db"
	"github.com/platform/notification-service/internal/handler"
	kafkapkg "github.com/platform/notification-service/internal/kafka"
	"github.com/platform/notification-service/internal/notification"
	redisstore "github.com/platform/notification-service/internal/redis"
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

	// ── Channel adapters ──────────────────────────────────────────────────────
	emailSender := channel.NewEmailSender(cfg.EmailProvider, cfg.SendGridAPIKey)
	pushSender := channel.NewPushSender(cfg.PushProvider, cfg.FCMServerKey)

	// ── External clients ──────────────────────────────────────────────────────
	broadcasterClient := client.NewBroadcasterClient(cfg.BroadcasterServiceURL)

	// ── Core service + router ─────────────────────────────────────────────────
	svc := notification.NewService(
		postgres, store, emailSender, pushSender,
		cfg.PushRateLimitPerHour, cfg.EmailRateLimitPerDay)

	router := notification.NewRouter(store, broadcasterClient)

	// ── Kafka consumer (multi-topic) ──────────────────────────────────────────
	consumer := kafkapkg.NewConsumer(
		cfg.KafkaBrokers,
		cfg.KafkaGroupID,
		cfg.KafkaTippingTopic,
		cfg.KafkaStreamingTopic,
		cfg.KafkaModerationTopic,
		cfg.KafkaPrivateShowTopic,
		cfg.KafkaUserProfileTopic,
	)
	defer consumer.Close()

	// ── HTTP server ───────────────────────────────────────────────────────────
	jwtValidator, err := auth.NewValidator(cfg.JWTSecret)
	if err != nil {
		log.Fatal().Err(err).Msg("jwt validator init failed")
	}
	h := handler.New(postgres, jwtValidator)

	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      h.Routes(),
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// ── Event pipeline ─────────────────────────────────────────────────────────
	// Buffer up to 1000 events to absorb burst traffic without blocking consumers.
	events := make(chan notification.RawEvent, 1000)

	g, gctx := errgroup.WithContext(ctx)

	// HTTP server
	g.Go(func() error {
		log.Info().Str("port", cfg.Port).Msg("notification-service listening")
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			return err
		}
		return nil
	})

	// Kafka consumers → events channel
	g.Go(func() error {
		return consumer.Run(gctx, events)
	})

	// Event processor: route + dispatch
	g.Go(func() error {
		for {
			select {
			case <-gctx.Done():
				return nil
			case ev := <-events:
				reqs := router.Route(gctx, ev)
				for _, req := range reqs {
					svc.Process(gctx, req)
				}
			}
		}
	})

	// Graceful shutdown
	g.Go(func() error {
		<-gctx.Done()
		log.Info().Msg("shutting down notification-service")
		shutCtx, shutCancel := context.WithTimeout(context.Background(), 15*time.Second)
		defer shutCancel()
		return srv.Shutdown(shutCtx)
	})

	if err := g.Wait(); err != nil {
		log.Error().Err(err).Msg("notification-service exited with error")
		os.Exit(1)
	}
}
