package config

import (
	"os"
	"strconv"
	"strings"
)

type Config struct {
	Port                string
	JWTSecret           string
	DBURL               string
	RedisURL            string
	KafkaBrokers        []string
	KafkaStreamingTopic string
	KafkaPresenceTopic  string
	KafkaGroupID        string
	StreamingProvider   string // "IVS" | "LIVEKIT_CLOUD" | "MOCK"
	AWSRegion           string
	PromotionThreshold  int64
	LogLevel            string
}

func Load() *Config {
	return &Config{
		Port:                envOrDefault("PORT", "8088"),
		JWTSecret:           mustEnv("JWT_SECRET"),
		DBURL:               envOrDefault("DB_URL", "postgres://postgres:postgres@localhost:5439/streaming_db"),
		RedisURL:            envOrDefault("REDIS_URL", "redis://localhost:6379"),
		KafkaBrokers:        strings.Split(envOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"), ","),
		KafkaStreamingTopic: envOrDefault("KAFKA_STREAMING_TOPIC", "streaming.events"),
		KafkaPresenceTopic:  envOrDefault("KAFKA_PRESENCE_TOPIC", "presence.events"),
		KafkaGroupID:        envOrDefault("KAFKA_GROUP_ID", "streaming-orchestration-service"),
		StreamingProvider:   strings.ToUpper(envOrDefault("STREAMING_PROVIDER", "MOCK")),
		AWSRegion:           envOrDefault("AWS_REGION", "us-east-1"),
		PromotionThreshold:  int64OrDefault("PROMOTION_THRESHOLD", 1000),
		LogLevel:            envOrDefault("LOG_LEVEL", "info"),
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

func int64OrDefault(key string, def int64) int64 {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.ParseInt(v, 10, 64); err == nil {
			return n
		}
	}
	return def
}
