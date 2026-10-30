# AuthSecured 1.3.0

Two-factor login, session control, and a round of pre-login hardening.

## What's new

- Two-factor authentication (TOTP) with an authenticator app: `/2fa enable`, `/2fa confirm <code>`,
  `/2fa disable <code>`, `/2fa status`. Eight backup codes, wrong codes count like wrong passwords.
- `/sessions` shows your saved sessions; `/sessions revoke` ends them without kicking you.
- A notice when you log in from a different address than last time.
- Weak passwords are rejected: a bundled list of common passwords, and a password equal to your name.
- Optional `auth.online-mode.trust-proxy-uuid` for offline backends behind an online-mode proxy.
- `/authadmin doctor` warns about PostgreSQL `ssl-mode: prefer` on remote hosts and about proxies
  that do not forward player IPs.

## Security and fixes

- Paper: portals, inventory drag, armor stands and item use after a cancelled block use are
  blocked before login.
- Fabric: players are kicked if AuthSecured fails to start, instead of being left frozen.
- `sTeve` can no longer lock out `Steve`; parallel `/login` commands can no longer outrun the lockout.
- SQLite uses WAL, so concurrent writes no longer fail with "database is locked".
- Shutdown waits for running password checks.

## Notes

- The default minimum password length is now 8 (was 10). Existing passwords are not affected.
- The database is migrated to a new schema on first start. Back it up first: 1.2.x jars will not
  start on the migrated database.
- Not tested on a live server yet: the PostgreSQL migrations and Folia are covered only by the unit
  tests (SQLite). See `docs/MANUAL_TESTING.md`.
- Paper and Fabric remain separate downloads; each Fabric file targets one exact Minecraft
  version and requires Fabric API.
