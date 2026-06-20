package client

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/google/uuid"
)

type BroadcasterClient struct {
	httpClient *http.Client
	baseURL    string
}

func NewBroadcasterClient(baseURL string) *BroadcasterClient {
	return &BroadcasterClient{
		baseURL: baseURL,
		httpClient: &http.Client{
			Timeout: 5 * time.Second,
			Transport: &http.Transport{
				MaxIdleConnsPerHost: 10,
				IdleConnTimeout:     60 * time.Second,
			},
		},
	}
}

type broadcasterProfile struct {
	DisplayName string `json:"displayName"`
}

// GetDisplayName fetches a broadcaster's display name.
// Returns an empty string on error so the caller can degrade gracefully.
func (c *BroadcasterClient) GetDisplayName(ctx context.Context, broadcasterID uuid.UUID) (string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet,
		fmt.Sprintf("%s/v1/broadcasters/%s", c.baseURL, broadcasterID), nil)
	if err != nil {
		return "", err
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("broadcaster-service request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		return "", nil
	}
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("broadcaster-service returned %d", resp.StatusCode)
	}

	var profile broadcasterProfile
	if err := json.NewDecoder(resp.Body).Decode(&profile); err != nil {
		return "", fmt.Errorf("decode broadcaster profile: %w", err)
	}
	return profile.DisplayName, nil
}
