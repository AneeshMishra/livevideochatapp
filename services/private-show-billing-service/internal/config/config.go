package config

import (
	"os"
	"strconv"
	"strings"
)

type Config struct {
	Port              string
	JWTSecret         string
	DatabaseURL       string
	RedisURL          string
	KafkaBrokers      []string
	KafkaPrivateTopic string
	KafkaStreamTopic  string
	KafkaGroupID      string
	WalletServiceURL  string
	BillingIntervalSec int // how often the poller checks for due ticks (default 10)
	LogLevel          string
}

func Load() *Config {
	return &Config{
		Port:               envOrDefault("PORT", "8091"),
		JWTSecret:          mustEnv("JWT_SECRET"),
		DatabaseURL:        mustEnv("DB_URL"),
		RedisURL:           envOrDefault("REDIS_URL", "redis://localhost:6379"),
		KafkaBrokers:       strings.Split(envOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"), ","),
		KafkaPrivateTopic:  envOrDefault("KAFKA_PRIVATE_SHOW_TOPIC", "private-show.events"),
		KafkaStreamTopic:   envOrDefault("KAFKA_STREAMING_TOPIC", "streaming.events"),
		KafkaGroupID:       envOrDefault("KAFKA_GROUP_ID", "private-show-billing-service"),
		WalletServiceURL:   envOrDefault("WALLET_SERVICE_URL", "http://wallet-service:8083"),
		BillingIntervalSec: intOrDefault("BILLING_INTERVAL_SEC", 10),
		LogLevel:           envOrDefault("LOG_LEVEL", "info"),
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
