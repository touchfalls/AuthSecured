CREATE TABLE IF NOT EXISTS accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid TEXT NOT NULL UNIQUE,
    username TEXT NOT NULL,
    username_normalized TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TEXT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    last_login_at TEXT NULL,
    password_changed_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_accounts_uuid ON accounts(uuid);
CREATE INDEX IF NOT EXISTS idx_accounts_username_normalized ON accounts(username_normalized);

CREATE TABLE IF NOT EXISTS login_attempts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NULL REFERENCES accounts(id) ON DELETE SET NULL,
    ip_hash BLOB NOT NULL,
    success INTEGER NOT NULL,
    reason TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_created_at ON login_attempts(created_at);
CREATE INDEX IF NOT EXISTS idx_login_attempts_ip_hash ON login_attempts(ip_hash);
CREATE INDEX IF NOT EXISTS idx_login_attempts_account_id ON login_attempts(account_id);

CREATE TABLE IF NOT EXISTS sessions (
    id TEXT PRIMARY KEY,
    account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token_hash BLOB NULL,
    server_id TEXT NULL,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    revoked_at TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_sessions_account_id ON sessions(account_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at);

CREATE TABLE IF NOT EXISTS security_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NULL REFERENCES accounts(id) ON DELETE SET NULL,
    event_type TEXT NOT NULL,
    ip_hash BLOB NULL,
    metadata TEXT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_security_events_created_at ON security_events(created_at);
CREATE INDEX IF NOT EXISTS idx_security_events_account_id ON security_events(account_id);
CREATE INDEX IF NOT EXISTS idx_security_events_type ON security_events(event_type);
