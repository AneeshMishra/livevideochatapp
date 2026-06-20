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

// EmailSender dispatches email notifications.
type EmailSender interface {
	Send(ctx context.Context, toUserID, toEmail, subject, body string) error
}

// MockEmailSender logs instead of sending — used in dev/CI.
type MockEmailSender struct{}

func (m *MockEmailSender) Send(_ context.Context, toUserID, toEmail, subject, body string) error {
	log.Info().
		Str("channel", "EMAIL").
		Str("provider", "MOCK").
		Str("user_id", toUserID).
		Str("subject", subject).
		Msgf("📧 [MOCK EMAIL] to=%s | %s", toEmail, body)
	return nil
}

// SendGridEmailSender sends via SendGrid's v3 mail/send API.
type SendGridEmailSender struct {
	apiKey     string
	fromEmail  string
	fromName   string
	httpClient *http.Client
}

func NewSendGridEmailSender(apiKey, fromEmail, fromName string) *SendGridEmailSender {
	return &SendGridEmailSender{
		apiKey:    apiKey,
		fromEmail: fromEmail,
		fromName:  fromName,
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

func (s *SendGridEmailSender) Send(ctx context.Context, _, toEmail, subject, body string) error {
	payload := map[string]any{
		"personalizations": []map[string]any{
			{"to": []map[string]string{{"email": toEmail}}},
		},
		"from":    map[string]string{"email": s.fromEmail, "name": s.fromName},
		"subject": subject,
		"content": []map[string]string{
			{"type": "text/plain", "value": body},
		},
	}
	data, _ := json.Marshal(payload)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		"https://api.sendgrid.com/v3/mail/send", bytes.NewReader(data))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+s.apiKey)
	req.Header.Set("Content-Type", "application/json")

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("sendgrid request: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return fmt.Errorf("sendgrid returned %d", resp.StatusCode)
	}
	return nil
}

// NewEmailSender constructs the appropriate sender based on provider name.
func NewEmailSender(provider, apiKey string) EmailSender {
	switch provider {
	case "SENDGRID":
		return NewSendGridEmailSender(apiKey, "noreply@platform.example.com", "Platform")
	default:
		return &MockEmailSender{}
	}
}
