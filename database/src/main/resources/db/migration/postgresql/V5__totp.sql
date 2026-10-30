-- Authenticator (TOTP): the secret is stored encrypted, backup codes only as keyed hashes.
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS totp_secret BYTEA NULL,
    ADD COLUMN IF NOT EXISTS totp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS totp_last_step BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS totp_backup_codes TEXT NULL;
