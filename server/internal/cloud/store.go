package cloud

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"time"

	_ "github.com/lib/pq"
)

var ErrConflict = errors.New("save revision conflict")

type SaveRecord struct {
	Revision  int64     `json:"revision"`
	Snapshot  Snapshot  `json:"snapshot"`
	SHA256    string    `json:"sha256"`
	UpdatedAt time.Time `json:"updatedAt"`
}

type Store interface {
	FindPassword(ctx context.Context, username string) (accountID, passwordHash string, err error)
	CreateSession(ctx context.Context, accountID, tokenHash string, expires time.Time) error
	AccountForToken(ctx context.Context, tokenHash string) (string, error)
	DeleteSession(ctx context.Context, tokenHash string) error
	GetSave(ctx context.Context, accountID string) (SaveRecord, error)
	PutSave(ctx context.Context, accountID string, baseRevision int64, snapshot Snapshot) (SaveRecord, error)
}

type PostgresStore struct{ DB *sql.DB }

func OpenPostgres(dsn string) (*PostgresStore, error) {
	if dsn == "" {
		return nil, errors.New("FENGSHEN_DATABASE_URL is required")
	}
	db, err := sql.Open("postgres", dsn)
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(8)
	db.SetMaxIdleConns(4)
	db.SetConnMaxLifetime(30 * time.Minute)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := db.PingContext(ctx); err != nil {
		db.Close()
		return nil, err
	}
	return &PostgresStore{DB: db}, nil
}

func (p *PostgresStore) FindPassword(ctx context.Context, username string) (string, string, error) {
	var id, hash string
	err := p.DB.QueryRowContext(ctx, "SELECT id,password_hash FROM accounts WHERE username=$1", username).Scan(&id, &hash)
	return id, hash, err
}
func (p *PostgresStore) CreateSession(ctx context.Context, accountID, tokenHash string, expires time.Time) error {
	_, err := p.DB.ExecContext(ctx, "INSERT INTO sessions(token_hash,account_id,expires_at) VALUES($1,$2,$3)", tokenHash, accountID, expires)
	return err
}
func (p *PostgresStore) AccountForToken(ctx context.Context, tokenHash string) (string, error) {
	var id string
	err := p.DB.QueryRowContext(ctx, "SELECT account_id FROM sessions WHERE token_hash=$1 AND expires_at>now()", tokenHash).Scan(&id)
	return id, err
}
func (p *PostgresStore) DeleteSession(ctx context.Context, tokenHash string) error {
	_, err := p.DB.ExecContext(ctx, "DELETE FROM sessions WHERE token_hash=$1", tokenHash)
	return err
}
func (p *PostgresStore) GetSave(ctx context.Context, accountID string) (SaveRecord, error) {
	var record SaveRecord
	var raw []byte
	err := p.DB.QueryRowContext(ctx, "SELECT revision,snapshot,snapshot_sha256,updated_at FROM save_slots WHERE account_id=$1 AND slot='main'", accountID).
		Scan(&record.Revision, &raw, &record.SHA256, &record.UpdatedAt)
	if err != nil {
		return SaveRecord{}, err
	}
	if err := json.Unmarshal(raw, &record.Snapshot); err != nil {
		return SaveRecord{}, err
	}
	hash, _, err := record.Snapshot.Digest()
	if err != nil || hash != record.SHA256 {
		return SaveRecord{}, errors.New("stored snapshot failed integrity validation")
	}
	return record, nil
}
func (p *PostgresStore) PutSave(ctx context.Context, accountID string, baseRevision int64, snapshot Snapshot) (SaveRecord, error) {
	if baseRevision < 0 {
		return SaveRecord{}, errors.New("invalid base revision")
	}
	hash, raw, err := snapshot.Digest()
	if err != nil {
		return SaveRecord{}, err
	}
	var record SaveRecord
	if baseRevision == 0 {
		err = p.DB.QueryRowContext(ctx, `INSERT INTO save_slots(account_id,slot,revision,content_version,snapshot,snapshot_sha256)
			VALUES($1,'main',1,$2,$3,$4) ON CONFLICT DO NOTHING RETURNING revision,updated_at`,
			accountID, snapshot.ContentVersion, string(raw), hash).Scan(&record.Revision, &record.UpdatedAt)
	} else {
		err = p.DB.QueryRowContext(ctx, `UPDATE save_slots SET revision=revision+1,content_version=$1,snapshot=$2,
			snapshot_sha256=$3,updated_at=now() WHERE account_id=$4 AND slot='main' AND revision=$5
			RETURNING revision,updated_at`, snapshot.ContentVersion, string(raw), hash, accountID, baseRevision).
			Scan(&record.Revision, &record.UpdatedAt)
	}
	if errors.Is(err, sql.ErrNoRows) {
		return SaveRecord{}, ErrConflict
	}
	if err != nil {
		return SaveRecord{}, err
	}
	record.Snapshot = snapshot
	record.SHA256 = hash
	return record, nil
}

func (p *PostgresStore) CreateAccount(ctx context.Context, username, password string, reset bool) error {
	hash, err := HashPassword(password)
	if err != nil {
		return err
	}
	if reset {
		tx, err := p.DB.BeginTx(ctx, nil)
		if err != nil {
			return err
		}
		defer tx.Rollback()
		result, err := tx.ExecContext(ctx, "UPDATE accounts SET password_hash=$1 WHERE username=$2", hash, username)
		if err != nil {
			return err
		}
		n, _ := result.RowsAffected()
		if n == 0 {
			return sql.ErrNoRows
		}
		if _, err = tx.ExecContext(ctx, "DELETE FROM sessions WHERE account_id=(SELECT id FROM accounts WHERE username=$1)", username); err != nil {
			return err
		}
		return tx.Commit()
	}
	id, err := RandomToken()
	if err != nil {
		return err
	}
	_, err = p.DB.ExecContext(ctx, "INSERT INTO accounts(id,username,password_hash) VALUES($1,$2,$3)", id, username, hash)
	return err
}
