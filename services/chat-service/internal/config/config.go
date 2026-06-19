package config

import (
	"os"
	"strconv"
	"strings"
)

type Config struct {
	Port               string
	JWTSecret          string
	RedisURL           string
	ScyllaHosts        []string
	ScyllaKeyspace     string
	KafkaBrokers       []string
	KafkaTippingTopic  string
	KafkaGroupID       string
	LogLevel           string
	MaxMessageBytes    int64
	RateLimitMessages  int
	RateLimitWindowSec int
}

func Load() *Config {
	return &Config{
		Port:               envOrDefault("PORT", "8086"),
		JWTSecret:          mustEnv("JWT_SECRET"),
		RedisURL:           envOrDefault("REDIS_URL", "redis://localhost:6379"),
		ScyllaHosts:        strings.Split(envOrDefault("SCYLLA_HOSTS", "localhost:9042"), ","),
		ScyllaKeyspace:     envOrDefault("SCYLLA_KEYSPACE", "chat"),
		KafkaBrokers:       strings.Split(envOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"), ","),
		KafkaTippingTopic:  envOrDefault("KAFKA_TIPPING_TOPIC", "tipping.events"),
		KafkaGroupID:       envOrDefault("KAFKA_GROUP_ID", "chat-service"),
		LogLevel:           envOrDefault("LOG_LEVEL", "info"),
		MaxMessageBytes:    int64OrDefault("MAX_MESSAGE_BYTES", 512),
		RateLimitMessages:  intOrDefault("RATE_LIMIT_MESSAGES", 5),
		RateLimitWindowSec: intOrDefault("RATE_LIMIT_WINDOW_SEC", 5),
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

func int64OrDefault(key string, def int64) int64 {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.ParseInt(v, 10, 64); err == nil {
			return n
		}
	}
	return def
}
