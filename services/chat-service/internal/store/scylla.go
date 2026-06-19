package store

import (
	"context"
	"fmt"
	"time"

	"github.com/gocql/gocql"
	"github.com/google/uuid"

	"github.com/platform/chat-service/internal/message"
)

const (
	createKeyspaceQ = `CREATE KEYSPACE IF NOT EXISTS %s
		WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
		AND durable_writes = true`

	createMessagesQ = `CREATE TABLE IF NOT EXISTS %s.messages (
		room_id     UUID,
		date_bucket TEXT,
		created_at  TIMESTAMP,
		message_id  UUID,
		user_id     UUID,
		username    TEXT,
		content     TEXT,
		msg_type    TEXT,
		PRIMARY KEY ((room_id, date_bucket), created_at, message_id)
	) WITH CLUSTERING ORDER BY (created_at DESC, message_id DESC)
	  AND default_time_to_live = 604800`
	// 604800 s = 7 days — chat history TTL
)

type ScyllaStore struct {
	session  *gocql.Session
	keyspace string
}

func NewScyllaStore(hosts []string, keyspace string) (*ScyllaStore, error) {
	// Bootstrap: connect to system keyspace first to create our keyspace.
	bootstrap := gocql.NewCluster(hosts...)
	bootstrap.Keyspace = "system"
	bootstrap.Consistency = gocql.LocalQuorum
	bootstrap.ConnectTimeout = 15 * time.Second
	bootstrap.Timeout = 10 * time.Second
	bootstrap.RetryPolicy = &gocql.ExponentialBackoffRetryPolicy{NumRetries: 5}

	sys, err := bootstrap.CreateSession()
	if err != nil {
		return nil, fmt.Errorf("connect to ScyllaDB system: %w", err)
	}
	if err := sys.Query(fmt.Sprintf(createKeyspaceQ, keyspace)).Exec(); err != nil {
		sys.Close()
		return nil, fmt.Errorf("create keyspace %s: %w", keyspace, err)
	}
	sys.Close()

	cluster := gocql.NewCluster(hosts...)
	cluster.Keyspace = keyspace
	cluster.Consistency = gocql.LocalQuorum
	cluster.ConnectTimeout = 15 * time.Second
	cluster.Timeout = 10 * time.Second
	cluster.RetryPolicy = &gocql.ExponentialBackoffRetryPolicy{NumRetries: 3}

	session, err := cluster.CreateSession()
	if err != nil {
		return nil, fmt.Errorf("connect to ScyllaDB keyspace %s: %w", keyspace, err)
	}
	if err := session.Query(fmt.Sprintf(createMessagesQ, keyspace)).Exec(); err != nil {
		session.Close()
		return nil, fmt.Errorf("create messages table: %w", err)
	}

	return &ScyllaStore{session: session, keyspace: keyspace}, nil
}

func (s *ScyllaStore) Close() {
	s.session.Close()
}

func (s *ScyllaStore) Ping(ctx context.Context) error {
	return s.session.Query("SELECT now() FROM system.local").
		WithContext(ctx).Exec()
}

func (s *ScyllaStore) SaveMessage(ctx context.Context, rec *message.ChatRecord) error {
	return s.session.Query(
		`INSERT INTO messages
		 (room_id, date_bucket, created_at, message_id, user_id, username, content, msg_type)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
		rec.RoomID, rec.DateBucket, rec.CreatedAt, rec.MessageID,
		rec.UserID, rec.Username, rec.Content, rec.MsgType,
	).WithContext(ctx).Exec()
}

// GetHistory returns up to limit recent messages for today's bucket, newest first.
func (s *ScyllaStore) GetHistory(ctx context.Context, roomID uuid.UUID, limit int) ([]*message.ChatRecord, error) {
	bucket := time.Now().UTC().Format("2006-01-02")
	return s.queryMessages(ctx,
		`SELECT room_id, date_bucket, created_at, message_id, user_id, username, content, msg_type
		 FROM messages WHERE room_id = ? AND date_bucket = ?
		 ORDER BY created_at DESC, message_id DESC LIMIT ?`,
		roomID, bucket, limit,
	)
}

// GetHistoryBefore returns messages older than `before` (for cursor pagination).
func (s *ScyllaStore) GetHistoryBefore(ctx context.Context, roomID uuid.UUID, before time.Time, limit int) ([]*message.ChatRecord, error) {
	bucket := before.UTC().Format("2006-01-02")
	return s.queryMessages(ctx,
		`SELECT room_id, date_bucket, created_at, message_id, user_id, username, content, msg_type
		 FROM messages WHERE room_id = ? AND date_bucket = ? AND created_at < ?
		 ORDER BY created_at DESC, message_id DESC LIMIT ?`,
		roomID, bucket, before, limit,
	)
}

func (s *ScyllaStore) queryMessages(ctx context.Context, cql string, args ...any) ([]*message.ChatRecord, error) {
	iter := s.session.Query(cql, args...).WithContext(ctx).Iter()
	var records []*message.ChatRecord
	var r message.ChatRecord
	for iter.Scan(&r.RoomID, &r.DateBucket, &r.CreatedAt, &r.MessageID,
		&r.UserID, &r.Username, &r.Content, &r.MsgType) {
		cp := r
		records = append(records, &cp)
	}
	return records, iter.Close()
}
