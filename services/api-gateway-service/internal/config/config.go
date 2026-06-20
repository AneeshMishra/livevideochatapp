package config

import (
	"os"
	"strconv"
	"strings"
)

type Config struct {
	Port      string
	JWTSecret string
	RedisURL  string
	LogLevel  string

	// Downstream service base URLs
	IdentityAuthURL  string
	UserProfileURL   string
	BroadcasterURL   string
	WalletURL        string
	PaymentsURL      string
	TippingURL       string
	ChatURL          string
	PresenceURL      string
	StreamingURL     string
	CatalogURL       string
	ModerationURL    string
	PrivateShowURL   string
	NotificationURL  string

	// Rate limiting (requests per minute)
	RateLimitAnonymousPerMin     int
	RateLimitAuthenticatedPerMin int

	// CORS
	AllowedOrigins []string
}

func Load() *Config {
	return &Config{
		Port:      getEnv("PORT", "8000"),
		JWTSecret: getEnv("JWT_SECRET", ""),
		RedisURL:  getEnv("REDIS_URL", "redis://localhost:6379"),
		LogLevel:  getEnv("LOG_LEVEL", "info"),

		IdentityAuthURL: getEnv("IDENTITY_AUTH_URL", "http://identity-auth-service:8081"),
		UserProfileURL:  getEnv("USER_PROFILE_URL", "http://user-profile-service:8082"),
		BroadcasterURL:  getEnv("BROADCASTER_URL", "http://broadcaster-service:8080"),
		WalletURL:       getEnv("WALLET_URL", "http://wallet-service:8083"),
		PaymentsURL:     getEnv("PAYMENTS_URL", "http://payments-service:8084"),
		TippingURL:      getEnv("TIPPING_URL", "http://tipping-service:8085"),
		ChatURL:         getEnv("CHAT_URL", "http://chat-service:8086"),
		PresenceURL:     getEnv("PRESENCE_URL", "http://presence-service:8087"),
		StreamingURL:    getEnv("STREAMING_URL", "http://streaming-orchestration-service:8088"),
		CatalogURL:      getEnv("CATALOG_URL", "http://catalog-discovery-service:8089"),
		ModerationURL:   getEnv("MODERATION_URL", "http://moderation-service:8090"),
		PrivateShowURL:  getEnv("PRIVATE_SHOW_URL", "http://private-show-billing-service:8091"),
		NotificationURL: getEnv("NOTIFICATION_URL", "http://notification-service:8092"),

		RateLimitAnonymousPerMin:     getEnvInt("RATE_LIMIT_ANONYMOUS_PER_MIN", 30),
		RateLimitAuthenticatedPerMin: getEnvInt("RATE_LIMIT_AUTH_PER_MIN", 120),

		AllowedOrigins: strings.Split(getEnv("ALLOWED_ORIGINS", "*"), ","),
	}
}

func getEnv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func getEnvInt(key string, def int) int {
	if v := os.Getenv(key); v != "" {
		if i, err := strconv.Atoi(v); err == nil {
			return i
		}
	}
	return def
}
