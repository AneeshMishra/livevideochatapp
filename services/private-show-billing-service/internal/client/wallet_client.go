package client

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/google/uuid"
)

// ErrInsufficientFunds is returned when the wallet service signals the viewer
// does not have enough tokens to cover the billing tick.
var ErrInsufficientFunds = fmt.Errorf("insufficient tokens")

type WalletClient struct {
	httpClient   *http.Client
	baseURL      string
	serviceToken string
}

func NewWalletClient(baseURL, jwtSecret string) (*WalletClient, error) {
	token, err := buildServiceToken(jwtSecret)
	if err != nil {
		return nil, fmt.Errorf("build service token: %w", err)
	}
	return &WalletClient{
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
			Transport: &http.Transport{
				MaxIdleConnsPerHost: 20,
				IdleConnTimeout:     60 * time.Second,
			},
		},
		baseURL:      baseURL,
		serviceToken: token,
	}, nil
}

type transferRequest struct {
	FromUserID              uuid.UUID `json:"fromUserId"`
	ToUserID                uuid.UUID `json:"toUserId"`
	GrossAmount             int64     `json:"grossAmount"`
	SenderTransactionType   string    `json:"senderTransactionType"`
	ReceiverTransactionType string    `json:"receiverTransactionType"`
	ReferenceID             uuid.UUID `json:"referenceId"`
	ReferenceType           string    `json:"referenceType"`
	IdempotencyKey          string    `json:"idempotencyKey"`
}

type TransferResult struct {
	SenderTx   TxSummary `json:"senderTx"`
	ReceiverTx TxSummary `json:"receiverTx"`
}

type TxSummary struct {
	ID           uuid.UUID `json:"id"`
	Amount       int64     `json:"amount"`
	BalanceAfter int64     `json:"balanceAfter"`
}

// Transfer debits grossAmount from viewerID and credits broadcasterID.
// idempotencyKey must be globally unique — use "{sessionId}-tick-{minuteNumber}".
func (c *WalletClient) Transfer(ctx context.Context,
	viewerID, broadcasterID uuid.UUID,
	grossAmount int64, referenceID uuid.UUID, idempotencyKey string) (*TransferResult, error) {

	body := transferRequest{
		FromUserID:              viewerID,
		ToUserID:                broadcasterID,
		GrossAmount:             grossAmount,
		SenderTransactionType:   "PRIVATE_SHOW_DEBIT",
		ReceiverTransactionType: "PRIVATE_SHOW_CREDIT",
		ReferenceID:             referenceID,
		ReferenceType:           "private_show",
		IdempotencyKey:          idempotencyKey,
	}

	data, _ := json.Marshal(body)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		c.baseURL+"/api/v1/wallet/internal/transfer", bytes.NewReader(data))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+c.serviceToken)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("wallet request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusUnprocessableEntity {
		return nil, ErrInsufficientFunds
	}
	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusCreated {
		return nil, fmt.Errorf("wallet service returned %d", resp.StatusCode)
	}

	var result TransferResult
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("decode wallet response: %w", err)
	}
	return &result, nil
}

// buildServiceToken creates a minimal HS256 JWT for service-to-service calls.
// The wallet-service accepts any JWT with role ADMIN signed with the shared secret.
func buildServiceToken(base64Secret string) (string, error) {
	secret, err := base64.StdEncoding.DecodeString(base64Secret)
	if err != nil {
		secret = []byte(base64Secret)
	}

	now := time.Now().Unix()
	headerB64 := base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"HS256","typ":"JWT"}`))
	payloadJSON := fmt.Sprintf(
		`{"sub":"private-show-billing-service","roles":["ADMIN"],"iat":%d,"exp":%d}`,
		now, now+365*24*3600)
	payloadB64 := base64.RawURLEncoding.EncodeToString([]byte(payloadJSON))

	signingInput := headerB64 + "." + payloadB64
	mac := hmac.New(sha256.New, secret)
	mac.Write([]byte(signingInput))
	sig := base64.RawURLEncoding.EncodeToString(mac.Sum(nil))

	return signingInput + "." + sig, nil
}
