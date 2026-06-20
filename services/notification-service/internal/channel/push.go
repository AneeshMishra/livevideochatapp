package channel

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/rs/zerolog/log"
)

// PushSender dispatches push notifications to device tokens.
type PushSender interface {
	Send(ctx context.Context, token, title, body string, data map[string]any) error
}

// MockPushSender logs payloads — used in dev/CI.
type MockPushSender struct{}

func (m *MockPushSender) Send(_ context.Context, token, title, body string, _ map[string]any) error {
	log.Info().
		Str("channel", "PUSH").
		Str("provider", "MOCK").
		Str("token_prefix", safePrefix(token, 12)).
		Str("title", title).
		Msgf("📱 [MOCK PUSH] %s", body)
	return nil
}

// FCMPushSender sends via Firebase Cloud Messaging (Legacy HTTP API).
// Production should upgrade to FCM v1 HTTP API with OAuth2.
type FCMPushSender struct {
	serverKey  string
	httpClient *http.Client
}

func NewFCMPushSender(serverKey string) *FCMPushSender {
	return &FCMPushSender{
		serverKey: serverKey,
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

func (f *FCMPushSender) Send(ctx context.Context, token, title, body string, data map[string]any) error {
	payload := map[string]any{
		"to": token,
		"notification": map[string]string{
			"title": title,
			"body":  body,
		},
		"data": data,
	}
	raw, _ := json.Marshal(payload)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		"https://fcm.googleapis.com/fcm/send", bytes.NewReader(raw))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "key="+f.serverKey)
	req.Header.Set("Content-Type", "application/json")

	resp, err := f.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("fcm request: %w", err)
	}
	defer resp.Body.Close()

	var result struct {
		Failure int `json:"failure"`
	}
	if json.NewDecoder(resp.Body).Decode(&result) == nil && result.Failure > 0 {
		return fmt.Errorf("fcm reported %d failures", result.Failure)
	}
	return nil
}

// NewPushSender constructs the appropriate sender based on provider name.
func NewPushSender(provider, serverKey string) PushSender {
	switch provider {
	case "FCM":
		return NewFCMPushSender(serverKey)
	default:
		return &MockPushSender{}
	}
}

func safePrefix(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n] + "..."
}
