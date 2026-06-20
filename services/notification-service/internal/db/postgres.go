package db

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog/log"

	"github.com/platform/notification-service/internal/model"
)

type Postgres struct {
	pool *pgxpool.Pool
}

func NewPostgres(ctx context.Context, dsn string) (*Postgres, error) {
	cfg, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		return nil, fmt.Errorf("parse db config: %w", err)
	}
	cfg.MaxConns = 20
	cfg.MinConns = 2
	cfg.MaxConnLifetime = 30 * time.Minute

	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, fmt.Errorf("create pg pool: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		return nil, fmt.Errorf("pg ping: %w", err)
	}
	return &Postgres{pool: pool}, nil
}

func (p *Postgres) Close() { p.pool.Close() }

func (p *Postgres) Ping(ctx context.Context) error { return p.pool.Ping(ctx) }

func (p *Postgres) RunMigrations(ctx context.Context) error {
	_, filename, _, _ := runtime.Caller(0)
	migrationsDir := filepath.Join(filepath.Dir(filename), "migrations")
	entries, err := os.ReadDir(migrationsDir)
	if err != nil {
		return fmt.Errorf("read migrations: %w", err)
	}
	var files []string
	for _, e := range entries {
		if !e.IsDir() && strings.HasSuffix(e.Name(), ".sql") {
			files = append(files, filepath.Join(migrationsDir, e.Name()))
		}
	}
	sort.Strings(files)
	for _, f := range files {
		sql, err := os.ReadFile(f)
		if err != nil {
			return fmt.Errorf("read %s: %w", f, err)
		}
		if _, err := p.pool.Exec(ctx, string(sql)); err != nil {
			return fmt.Errorf("run %s: %w", f, err)
		}
		log.Info().Str("file", filepath.Base(f)).Msg("migration applied")
	}
	return nil
}

// ── Notification log ──────────────────────────────────────────────────────────

func (p *Postgres) InsertLog(ctx context.Context, l *model.NotificationLog) error {
	_, err := p.pool.Exec(ctx, `
		INSERT INTO notification_log
		    (id, user_id, event_type, channel, status, title, body, metadata, error)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8::jsonb,$9)`,
		l.ID, l.UserID, l.EventType, l.Channel,
		l.Status, l.Title, l.Body, l.Metadata, l.Error)
	return err
}

func (p *Postgres) ListLogByUser(ctx context.Context, userID uuid.UUID, limit, offset int) ([]*model.NotificationLog, error) {
	rows, err := p.pool.Query(ctx, `
		SELECT id, user_id, event_type, channel, status, title, body,
		       COALESCE(metadata::text,'{}'), COALESCE(error,''), created_at
		  FROM notification_log
		 WHERE user_id = $1
		 ORDER BY created_at DESC
		 LIMIT $2 OFFSET $3`,
		userID, limit, offset)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []*model.NotificationLog
	for rows.Next() {
		l := &model.NotificationLog{}
		if err := rows.Scan(&l.ID, &l.UserID, &l.EventType, &l.Channel,
			&l.Status, &l.Title, &l.Body, &l.Metadata, &l.Error, &l.CreatedAt); err != nil {
			return nil, err
		}
		out = append(out, l)
	}
	return out, rows.Err()
}

// ── Preferences ───────────────────────────────────────────────────────────────

func (p *Postgres) UpsertPreference(ctx context.Context, pref *model.UserPreference) error {
	_, err := p.pool.Exec(ctx, `
		INSERT INTO notification_preferences (id, user_id, event_type, channel, enabled)
		VALUES ($1,$2,$3,$4,$5)
		ON CONFLICT (user_id, event_type, channel)
		DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = now()`,
		pref.ID, pref.UserID, pref.EventType, pref.Channel, pref.Enabled)
	return err
}

func (p *Postgres) GetPreferences(ctx context.Context, userID uuid.UUID) ([]*model.UserPreference, error) {
	rows, err := p.pool.Query(ctx, `
		SELECT id, user_id, event_type, channel, enabled, created_at, updated_at
		  FROM notification_preferences WHERE user_id = $1`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []*model.UserPreference
	for rows.Next() {
		p := &model.UserPreference{}
		if err := rows.Scan(&p.ID, &p.UserID, &p.EventType, &p.Channel,
			&p.Enabled, &p.CreatedAt, &p.UpdatedAt); err != nil {
			return nil, err
		}
		out = append(out, p)
	}
	return out, rows.Err()
}

// IsChannelEnabled checks if a user has explicitly disabled a channel for an event type.
// Returns true (enabled) if no preference row exists (opt-out model for non-critical).
func (p *Postgres) IsChannelEnabled(ctx context.Context, userID uuid.UUID, eventType, channel string) (bool, error) {
	var enabled bool
	err := p.pool.QueryRow(ctx, `
		SELECT enabled FROM notification_preferences
		 WHERE user_id = $1 AND event_type = $2 AND channel = $3`,
		userID, eventType, channel).Scan(&enabled)
	if err != nil {
		// No row = user hasn't configured this preference = default ON
		return true, nil
	}
	return enabled, nil
}

// ── Device tokens ─────────────────────────────────────────────────────────────

func (p *Postgres) UpsertDeviceToken(ctx context.Context, dt *model.DeviceToken) error {
	_, err := p.pool.Exec(ctx, `
		INSERT INTO device_tokens (id, user_id, token, platform, active)
		VALUES ($1,$2,$3,$4,true)
		ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, active = true`,
		dt.ID, dt.UserID, dt.Token, dt.Platform)
	return err
}

func (p *Postgres) DeactivateDeviceToken(ctx context.Context, token string) error {
	_, err := p.pool.Exec(ctx, `UPDATE device_tokens SET active = false WHERE token = $1`, token)
	return err
}

func (p *Postgres) GetActiveDeviceTokens(ctx context.Context, userID uuid.UUID) ([]model.DeviceToken, error) {
	rows, err := p.pool.Query(ctx, `
		SELECT id, user_id, token, platform, active, created_at
		  FROM device_tokens
		 WHERE user_id = $1 AND active = true`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []model.DeviceToken
	for rows.Next() {
		var dt model.DeviceToken
		if err := rows.Scan(&dt.ID, &dt.UserID, &dt.Token, &dt.Platform, &dt.Active, &dt.CreatedAt); err != nil {
			return nil, err
		}
		out = append(out, dt)
	}
	return out, rows.Err()
}
