CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    username VARCHAR(16) NOT NULL,
    username_normalized VARCHAR(16) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ NULL,
    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_accounts_uuid ON accounts(uuid);
CREATE INDEX IF NOT EXISTS idx_accounts_username_normalized ON accounts(username_normalized);

CREATE TABLE IF NOT EXISTS login_attempts (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NULL REFERENCES accounts(id) ON DELETE SET NULL,
    ip_hash BYTEA NOT NULL,
    success BOOLEAN NOT NULL,
    reason VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_created_at ON login_attempts(created_at);
CREATE INDEX IF NOT EXISTS idx_login_attempts_ip_hash ON login_attempts(ip_hash);
CREATE INDEX IF NOT EXISTS idx_login_attempts_account_id ON login_attempts(account_id);

CREATE TABLE IF NOT EXISTS sessions (
    id UUID PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token_hash BYTEA NULL,
    server_id VARCHAR(64) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_sessions_account_id ON sessions(account_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at);

CREATE TABLE IF NOT EXISTS security_events (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NULL REFERENCES accounts(id) ON DELETE SET NULL,
    event_type VARCHAR(64) NOT NULL,
    ip_hash BYTEA NULL,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_security_events_created_at ON security_events(created_at);
CREATE INDEX IF NOT EXISTS idx_security_events_account_id ON security_events(account_id);
CREATE INDEX IF NOT EXISTS idx_security_events_type ON security_events(event_type);
