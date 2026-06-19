package store

import (
	"context"
	"embed"
	"fmt"
	"io/fs"
	"sort"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog/log"

	"github.com/platform/streaming-orchestration-service/internal/domain"
)

//go:embed migrations/*.sql
var migrationsFS embed.FS

type DB struct {
	pool *pgxpool.Pool
}

func NewDB(ctx context.Context, dsn string) (*DB, error) {
	cfg, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		return nil, fmt.Errorf("parse DSN: %w", err)
	}
	cfg.MaxConns = 20
	cfg.MinConns = 2
	cfg.MaxConnLifetime = 30 * time.Minute

	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, fmt.Errorf("create pool: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		return nil, fmt.Errorf("ping postgres: %w", err)
	}

	db := &DB{pool: pool}
	if err := db.runMigrations(ctx); err != nil {
		return nil, fmt.Errorf("migrations: %w", err)
	}
	return db, nil
}

func (db *DB) Close() { db.pool.Close() }

func (db *DB) Ping(ctx context.Context) error { return db.pool.Ping(ctx) }

// runMigrations applies embedded SQL migration files in alphabetical order.
func (db *DB) runMigrations(ctx context.Context) error {
	_, err := db.pool.Exec(ctx, `CREATE TABLE IF NOT EXISTS schema_migrations (
		version  TEXT PRIMARY KEY,
		applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
	)`)
	if err != nil {
		return err
	}

	entries, err := fs.ReadDir(migrationsFS, "migrations")
	if err != nil {
		return err
	}
	sort.Slice(entries, func(i, j int) bool { return entries[i].Name() < entries[j].Name() })

	for _, e := range entries {
		if !strings.HasSuffix(e.Name(), ".sql") {
			continue
		}
		var count int
		db.pool.QueryRow(ctx, "SELECT COUNT(*) FROM schema_migrations WHERE version=$1", e.Name()).Scan(&count)
		if count > 0 {
			continue
		}
		sql, err := fs.ReadFile(migrationsFS, "migrations/"+e.Name())
		if err != nil {
			return err
		}
		if _, err := db.pool.Exec(ctx, string(sql)); err != nil {
			return fmt.Errorf("apply %s: %w", e.Name(), err)
		}
		db.pool.Exec(ctx, "INSERT INTO schema_migrations(version) VALUES($1)", e.Name())
		log.Info().Str("migration", e.Name()).Msg("applied migration")
	}
	return nil
}

// ── Room repository ───────────────────────────────────────────────────────────

func (db *DB) CreateRoom(ctx context.Context, r *domain.Room) error {
	return db.pool.QueryRow(ctx, `
		INSERT INTO rooms
		  (id, broadcaster_id, title, status, delivery_mode,
		   ingest_endpoint, stream_key, hls_playback_url, webrtc_playback_url,
		   provider, provider_channel_id, promotion_threshold)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)
		RETURNING created_at, updated_at, version`,
		r.ID, r.BroadcasterID, r.Title, string(r.Status), string(r.DeliveryMode),
		r.IngestEndpoint, r.StreamKey, r.HLSPlaybackURL, r.WebRTCPlaybackURL,
		string(r.Provider), r.ProviderChannelID, r.PromotionThreshold,
	).Scan(&r.CreatedAt, &r.UpdatedAt, &r.Version)
}

func (db *DB) GetRoom(ctx context.Context, id uuid.UUID) (*domain.Room, error) {
	r := &domain.Room{}
	err := db.pool.QueryRow(ctx, `
		SELECT id, broadcaster_id, title, status, delivery_mode,
		       ingest_endpoint, stream_key, hls_playback_url, webrtc_playback_url,
		       provider, provider_channel_id, viewer_count, peak_viewer_count,
		       promotion_threshold, created_at, updated_at, version
		FROM rooms WHERE id=$1`, id,
	).Scan(
		&r.ID, &r.BroadcasterID, &r.Title, &r.Status, &r.DeliveryMode,
		&r.IngestEndpoint, &r.StreamKey, &r.HLSPlaybackURL, &r.WebRTCPlaybackURL,
		&r.Provider, &r.ProviderChannelID, &r.ViewerCount, &r.PeakViewerCount,
		&r.PromotionThreshold, &r.CreatedAt, &r.UpdatedAt, &r.Version,
	)
	if err == pgx.ErrNoRows {
		return nil, domain.ErrRoomNotFound{ID: id}
	}
	return r, err
}

func (db *DB) ListRoomsByBroadcaster(ctx context.Context, broadcasterID uuid.UUID) ([]*domain.Room, error) {
	rows, err := db.pool.Query(ctx, `
		SELECT id, broadcaster_id, title, status, delivery_mode,
		       ingest_endpoint, hls_playback_url,
		       viewer_count, peak_viewer_count, promotion_threshold,
		       created_at, updated_at, version
		FROM rooms WHERE broadcaster_id=$1 ORDER BY created_at DESC`, broadcasterID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var rooms []*domain.Room
	for rows.Next() {
		r := &domain.Room{}
		if err := rows.Scan(
			&r.ID, &r.BroadcasterID, &r.Title, &r.Status, &r.DeliveryMode,
			&r.IngestEndpoint, &r.HLSPlaybackURL,
			&r.ViewerCount, &r.PeakViewerCount, &r.PromotionThreshold,
			&r.CreatedAt, &r.UpdatedAt, &r.Version,
		); err != nil {
			return nil, err
		}
		rooms = append(rooms, r)
	}
	return rooms, rows.Err()
}

func (db *DB) SetRoomLive(ctx context.Context, id uuid.UUID, version int64) error {
	tag, err := db.pool.Exec(ctx,
		`UPDATE rooms SET status='LIVE', updated_at=now(), version=version+1
		 WHERE id=$1 AND version=$2`, id, version)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return fmt.Errorf("optimistic lock conflict on room %s", id)
	}
	return nil
}

func (db *DB) SetRoomOffline(ctx context.Context, id uuid.UUID, version int64) error {
	tag, err := db.pool.Exec(ctx,
		`UPDATE rooms SET status='OFFLINE', delivery_mode='WEBRTC',
		 updated_at=now(), version=version+1
		 WHERE id=$1 AND version=$2`, id, version)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return fmt.Errorf("optimistic lock conflict on room %s", id)
	}
	return nil
}

func (db *DB) SetDeliveryMode(ctx context.Context, id uuid.UUID, mode domain.DeliveryMode) error {
	_, err := db.pool.Exec(ctx,
		`UPDATE rooms SET delivery_mode=$1, updated_at=now(), version=version+1 WHERE id=$2`,
		string(mode), id)
	return err
}

func (db *DB) UpdateViewerStats(ctx context.Context, id uuid.UUID, count int64) error {
	_, err := db.pool.Exec(ctx, `
		UPDATE rooms
		SET viewer_count=$1,
		    peak_viewer_count=GREATEST(peak_viewer_count, $1),
		    updated_at=now()
		WHERE id=$2`, count, id)
	return err
}

func (db *DB) UpdateRoomTitle(ctx context.Context, id uuid.UUID, title string) error {
	_, err := db.pool.Exec(ctx,
		`UPDATE rooms SET title=$1, updated_at=now() WHERE id=$2`, title, id)
	return err
}

// ── StreamSession repository ──────────────────────────────────────────────────

func (db *DB) CreateSession(ctx context.Context, s *domain.StreamSession) error {
	return db.pool.QueryRow(ctx,
		`INSERT INTO stream_sessions (id, room_id, broadcaster_id, status)
		 VALUES ($1,$2,$3,'ACTIVE') RETURNING started_at`,
		s.ID, s.RoomID, s.BroadcasterID,
	).Scan(&s.StartedAt)
}

func (db *DB) EndSession(ctx context.Context, roomID uuid.UUID, peakViewers int64) error {
	_, err := db.pool.Exec(ctx, `
		UPDATE stream_sessions
		SET status='ENDED', ended_at=now(), peak_viewers=$1
		WHERE room_id=$2 AND status='ACTIVE'`, peakViewers, roomID)
	return err
}

func (db *DB) GetActiveSession(ctx context.Context, roomID uuid.UUID) (*domain.StreamSession, error) {
	s := &domain.StreamSession{}
	err := db.pool.QueryRow(ctx,
		`SELECT id, room_id, broadcaster_id, status, started_at, peak_viewers
		 FROM stream_sessions WHERE room_id=$1 AND status='ACTIVE'`, roomID,
	).Scan(&s.ID, &s.RoomID, &s.BroadcasterID, &s.Status, &s.StartedAt, &s.PeakViewers)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	return s, err
}
