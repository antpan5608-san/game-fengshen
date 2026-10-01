-- Fengshen-only database. Apply only to the dedicated fengshen database.
CREATE TABLE IF NOT EXISTS accounts (
    id text PRIMARY KEY,
    username text NOT NULL UNIQUE,
    password_hash text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sessions (
    token_hash char(64) PRIMARY KEY,
    account_id text NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS sessions_expires_at_idx ON sessions(expires_at);

CREATE TABLE IF NOT EXISTS save_slots (
    account_id text NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    slot text NOT NULL CHECK (slot = 'main'),
    revision bigint NOT NULL CHECK (revision > 0),
    content_version text NOT NULL,
    snapshot jsonb NOT NULL,
    snapshot_sha256 char(64) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(account_id, slot)
);
