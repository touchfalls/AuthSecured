-- IP hash of the previous successful login, used to tell a player when they log in from a new address.
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS last_login_ip_hash BYTEA NULL;
