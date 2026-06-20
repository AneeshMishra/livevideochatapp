package auth

import (
	"encoding/base64"
	"fmt"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

type Claims struct {
	UserID   uuid.UUID
	Username string
	Roles    []string
}

type Validator struct {
	secret []byte
}

func NewValidator(base64Secret string) (*Validator, error) {
	secret, err := base64.StdEncoding.DecodeString(base64Secret)
	if err != nil {
		secret = []byte(base64Secret)
	}
	if len(secret) == 0 {
		return nil, fmt.Errorf("JWT_SECRET must not be empty")
	}
	return &Validator{secret: secret}, nil
}

func (v *Validator) Validate(tokenString string) (*Claims, error) {
	tok, err := jwt.Parse(tokenString, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return v.secret, nil
	}, jwt.WithValidMethods([]string{"HS256"}))
	if err != nil || !tok.Valid {
		return nil, fmt.Errorf("invalid token: %w", err)
	}
	mc, ok := tok.Claims.(jwt.MapClaims)
	if !ok {
		return nil, fmt.Errorf("invalid claims")
	}
	sub, _ := mc.GetSubject()
	uid, err := uuid.Parse(sub)
	if err != nil {
		return nil, fmt.Errorf("subject not a UUID: %w", err)
	}
	var roles []string
	if r, ok := mc["roles"].([]any); ok {
		for _, role := range r {
			if s, ok := role.(string); ok {
				roles = append(roles, s)
			}
		}
	}
	username, _ := mc["username"].(string)
	return &Claims{UserID: uid, Username: username, Roles: roles}, nil
}

func (c *Claims) IsAdmin() bool {
	for _, r := range c.Roles {
		if r == "ADMIN" {
			return true
		}
	}
	return false
}
