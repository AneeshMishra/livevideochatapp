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
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog/log"

	"github.com/platform/private-show-billing-service/internal/model"
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
		return nil, fmt.Errorf("pg ping failed: %w", err)
	}
	return &Postgres{pool: pool}, nil
}

func (p *Postgres) Close() {
	p.pool.Close()
}

func (p *Postgres) Ping(ctx context.Context) error {
	return p.pool.Ping(ctx)
}

// RunMigrations executes all .sql files in the migrations directory, ordered by filename.
func (p *Postgres) RunMigrations(ctx context.Context) error {
	_, filename, _, _ := runtime.Caller(0)
	migrationsDir := filepath.Join(filepath.Dir(filename), "migrations")

	entries, err := os.ReadDir(migrationsDir)
	if err != nil {
		return fmt.Errorf("read migrations dir: %w", err)
	}

	var sqlFiles []string
	for _, e := range entries {
		if !e.IsDir() && strings.HasSuffix(e.Name(), ".sql") {
			sqlFiles = append(sqlFiles, filepath.Join(migrationsDir, e.Name()))
		}
	}
	sort.Strings(sqlFiles)

	for _, f := range sqlFiles {
		sql, err := os.ReadFile(f)
		if err != nil {
			return fmt.Errorf("read migration %s: %w", f, err)
		}
		if _, err := p.pool.Exec(ctx, string(sql)); err != nil {
			return fmt.Errorf("run migration %s: %w", f, err)
		}
		log.Info().Str("file", filepath.Base(f)).Msg("migration applied")
	}
	return nil
}

// ── Session CRUD ──────────────────────────────────────────────────────────────

func (p *Postgres) InsertSession(ctx context.Context, s *model.Session) error {
	_, err := p.pool.Exec(ctx, `
		INSERT INTO private_show_sessions
		    (id, viewer_id, broadcaster_id, room_id, show_type, status,
		     rate_per_minute, billed_minutes, total_tokens, started_at)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`,
		s.ID, s.ViewerID, s.BroadcasterID, s.RoomID,
		string(s.ShowType), string(s.Status),
		s.RatePerMinute, s.BilledMinutes, s.TotalTokens, s.StartedAt)
	return err
}

func (p *Postgres) GetSession(ctx context.Context, sessionID uuid.UUID) (*model.Session, error) {
	row := p.pool.QueryRow(ctx, `
		SELECT id, viewer_id, broadcaster_id, room_id, show_type, status,
		       rate_per_minute, billed_minutes, total_tokens,
		       started_at, ended_at, end_reason, created_at
		  FROM private_show_sessions WHERE id = $1`, sessionID)
	return scanSession(row)
}

func (p *Postgres) UpdateSessionEnd(ctx context.Context, sessionID uuid.UUID,
	billedMinutes int, totalTokens int64, endReason model.EndReason) error {
	_, err := p.pool.Exec(ctx, `
		UPDATE private_show_sessions
		   SET status = 'COMPLETED', billed_minutes = $2, total_tokens = $3,
		       ended_at = now(), end_reason = $4
		 WHERE id = $1`,
		sessionID, billedMinutes, totalTokens, string(endReason))
	return err
}

func (p *Postgres) UpdateSessionPause(ctx context.Context, sessionID uuid.UUID, paused bool) error {
	status := "ACTIVE"
	if paused {
		status = "PAUSED"
	}
	_, err := p.pool.Exec(ctx, `
		UPDATE private_show_sessions SET status = $2 WHERE id = $1`,
		sessionID, status)
	return err
}

func (p *Postgres) ListSessionsByViewer(ctx context.Context, viewerID uuid.UUID, limit, offset int) ([]*model.Session, error) {
	rows, err := p.pool.Query(ctx, `
		SELECT id, viewer_id, broadcaster_id, room_id, show_type, status,
		       rate_per_minute, billed_minutes, total_tokens,
		       started_at, ended_at, end_reason, created_at
		  FROM private_show_sessions
		 WHERE viewer_id = $1
		 ORDER BY started_at DESC LIMIT $2 OFFSET $3`,
		viewerID, limit, offset)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return collectSessions(rows)
}

func (p *Postgres) ListSessionsByBroadcaster(ctx context.Context, broadcasterID uuid.UUID, limit, offset int) ([]*model.Session, error) {
	rows, err := p.pool.Query(ctx, `
		SELECT id, viewer_id, broadcaster_id, room_id, show_type, status,
		       rate_per_minute, billed_minutes, total_tokens,
		       started_at, ended_at, end_reason, created_at
		  FROM private_show_sessions
		 WHERE broadcaster_id = $1
		 ORDER BY started_at DESC LIMIT $2 OFFSET $3`,
		broadcasterID, limit, offset)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return collectSessions(rows)
}

// ── Billing tick CRUD ─────────────────────────────────────────────────────────

func (p *Postgres) InsertBillingTick(ctx context.Context, t *model.BillingTick) error {
	_, err := p.pool.Exec(ctx, `
		INSERT INTO billing_ticks
		    (id, session_id, viewer_id, broadcaster_id, tokens_charged, minute_number, wallet_tx_id)
		VALUES ($1,$2,$3,$4,$5,$6,$7)
		ON CONFLICT (session_id, minute_number) DO NOTHING`,
		t.ID, t.SessionID, t.ViewerID, t.BroadcasterID,
		t.TokensCharged, t.MinuteNumber, t.WalletTxID)
	return err
}

func (p *Postgres) GetTickCount(ctx context.Context, sessionID uuid.UUID) (int, error) {
	var n int
	err := p.pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM billing_ticks WHERE session_id = $1`, sessionID).Scan(&n)
	return n, err
}

// ── helpers ───────────────────────────────────────────────────────────────────

func scanSession(row pgx.Row) (*model.Session, error) {
	s := &model.Session{}
	var showType, status string
	var endReason *string
	err := row.Scan(
		&s.ID, &s.ViewerID, &s.BroadcasterID, &s.RoomID,
		&showType, &status, &s.RatePerMinute,
		&s.BilledMinutes, &s.TotalTokens,
		&s.StartedAt, &s.EndedAt, &endReason, &s.CreatedAt)
	if err != nil {
		return nil, err
	}
	s.ShowType = model.ShowType(showType)
	s.Status = model.SessionStatus(status)
	if endReason != nil {
		r := model.EndReason(*endReason)
		s.EndReason = &r
	}
	return s, nil
}

func collectSessions(rows pgx.Rows) ([]*model.Session, error) {
	var out []*model.Session
	for rows.Next() {
		s := &model.Session{}
		var showType, status string
		var endReason *string
		if err := rows.Scan(
			&s.ID, &s.ViewerID, &s.BroadcasterID, &s.RoomID,
			&showType, &status, &s.RatePerMinute,
			&s.BilledMinutes, &s.TotalTokens,
			&s.StartedAt, &s.EndedAt, &endReason, &s.CreatedAt); err != nil {
			return nil, err
		}
		s.ShowType = model.ShowType(showType)
		s.Status = model.SessionStatus(status)
		if endReason != nil {
			r := model.EndReason(*endReason)
			s.EndReason = &r
		}
		out = append(out, s)
	}
	return out, rows.Err()
}
