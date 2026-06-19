package config

import (
	"os"
	"strconv"
	"strings"
)

type Config struct {
	Port                string
	JWTSecret           string
	RedisURL            string
	KafkaBrokers        []string
	KafkaPresenceTopic  string
	HeartbeatTTLSec     int // how long before a user is considered offline
	SweeperIntervalSec  int // how often the background count-snapshot goroutine runs
	LogLevel            string
}

func Load() *Config {
	return &Config{
		Port:               envOrDefault("PORT", "8087"),
		JWTSecret:          mustEnv("JWT_SECRET"),
		RedisURL:           envOrDefault("REDIS_URL", "redis://localhost:6379"),
		KafkaBrokers:       strings.Split(envOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"), ","),
		KafkaPresenceTopic: envOrDefault("KAFKA_PRESENCE_TOPIC", "presence.events"),
		HeartbeatTTLSec:    intOrDefault("HEARTBEAT_TTL_SEC", 30),
		SweeperIntervalSec: intOrDefault("SWEEPER_INTERVAL_SEC", 30),
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
