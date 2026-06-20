package config

import (
	"os"
	"strconv"
	"strings"
)

type Config struct {
	Port                  string
	JWTSecret             string
	DatabaseURL           string
	RedisURL              string
	KafkaBrokers          []string
	KafkaTippingTopic     string
	KafkaStreamingTopic   string
	KafkaModerationTopic  string
	KafkaPrivateShowTopic string
	KafkaUserProfileTopic string
	KafkaGroupID          string
	BroadcasterServiceURL string
	EmailProvider         string // MOCK | SENDGRID
	SendGridAPIKey        string
	PushProvider          string // MOCK | FCM
	FCMServerKey          string
	PushRateLimitPerHour  int
	EmailRateLimitPerDay  int
	LogLevel              string
}

func Load() *Config {
	return &Config{
		Port:                  envOrDefault("PORT", "8092"),
		JWTSecret:             mustEnv("JWT_SECRET"),
		DatabaseURL:           mustEnv("DB_URL"),
		RedisURL:              envOrDefault("REDIS_URL", "redis://localhost:6379"),
		KafkaBrokers:          strings.Split(envOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"), ","),
		KafkaTippingTopic:     envOrDefault("KAFKA_TIPPING_TOPIC", "tipping.events"),
		KafkaStreamingTopic:   envOrDefault("KAFKA_STREAMING_TOPIC", "streaming.events"),
		KafkaModerationTopic:  envOrDefault("KAFKA_MODERATION_TOPIC", "moderation.events"),
		KafkaPrivateShowTopic: envOrDefault("KAFKA_PRIVATE_SHOW_TOPIC", "private-show.events"),
		KafkaUserProfileTopic: envOrDefault("KAFKA_USER_PROFILE_TOPIC", "user-profile.events"),
		KafkaGroupID:          envOrDefault("KAFKA_GROUP_ID", "notification-service"),
		BroadcasterServiceURL: envOrDefault("BROADCASTER_SERVICE_URL", "http://broadcaster-service:8080"),
		EmailProvider:         envOrDefault("EMAIL_PROVIDER", "MOCK"),
		SendGridAPIKey:        os.Getenv("SENDGRID_API_KEY"),
		PushProvider:          envOrDefault("PUSH_PROVIDER", "MOCK"),
		FCMServerKey:          os.Getenv("FCM_SERVER_KEY"),
		PushRateLimitPerHour:  intOrDefault("PUSH_RATE_LIMIT_PER_HOUR", 20),
		EmailRateLimitPerDay:  intOrDefault("EMAIL_RATE_LIMIT_PER_DAY", 10),
		LogLevel:              envOrDefault("LOG_LEVEL", "info"),
	}
}

func mustEnv(key string) string {
	v := os.Getenv(key)
	if v == "" {
		panic("required env var not set: " + key)
	}
	return v
}

func envOrDefault(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func intOrDefault(key string, def int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return def
}
