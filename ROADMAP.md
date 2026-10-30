# Roadmap

Status of the hardening work that followed the 1.2.7 review (branch `security/hardening-after-1.2.7`).
Everything marked done has automated tests; none of it has been exercised on a live server yet. The
server checklist is in [docs/MANUAL_TESTING.md](docs/MANUAL_TESTING.md).

## Done

### Security fixes
- [x] Paper: portals, inventory drag, armor stands, `PlayerInteractAtEntityEvent` and item use after a
      cancelled block use are blocked before login (their handler lists differ from the parent events').
- [x] Fabric: when AuthSecured fails to initialize, joining players are kicked instead of staying frozen.
- [x] The account rate limit is keyed by the stored account, not the typed name, so `sTeve` can no longer
      lock `Steve` on an offline-mode server.
- [x] Only one password check per player at a time, so parallel `/login` commands cannot all pass a stale
      "not locked" snapshot.
- [x] Paper: an authenticated player is only replaced by a connection from the same address (also
      matched without case).
- [x] SQLite uses WAL and a 5 second busy timeout instead of failing with "database is locked".
- [x] Shutdown waits for running password checks before the database closes.
- [x] `/authadmin doctor` flags PostgreSQL `ssl-mode: prefer` on remote hosts.
- [x] `/authadmin doctor` warns when several players share one local address (proxy without IP forwarding).

### Features
- [x] Minimum password length defaults to 8 and stays configurable; the error message shows the real minimum.
- [x] Common passwords (bundled list) and a password equal to the player name are rejected.
- [x] `/sessions` shows the saved session count; `/sessions revoke` ends all of them and keeps the player online.
- [x] Notice when logging in from a different address than last time (audit event `LOGIN_NEW_ADDRESS`).
- [x] Two-factor authentication (TOTP): `/2fa enable|confirm|disable|status`, backup codes, replay protection,
      wrong codes counted like wrong passwords, secrets encrypted, an administrator password reset switches it off.
- [x] `auth.online-mode.trust-proxy-uuid`: an offline-mode backend behind an online-mode proxy can skip the
      password for forwarded Mojang UUIDs (off by default).

### Build and documentation
- [x] GitHub Actions pinned by commit SHA; Gradle distribution checksum; CodeQL workflow; unused Sonatype
      repository removed.
- [x] README and `SUPPORTED_VERSIONS.md` point at the current version; new commands are documented.
- [x] Manual server test checklist.

## Not done, and why

### Needs a live server first
- [ ] Fabric mixins may mutate the player on the network thread before the hop to the main thread
      (`ServerGamePacketListenerImplMixin`). Unconfirmed; needs a decompiled or live check on one target.
- [ ] Packets a vanilla client can send without a Bukkit event (command block, structure block, game mode
      switch for operators). Unconfirmed; needs a packet-level listener or a live test.
- [ ] Folia support is covered by unit tests only.
- [ ] The PostgreSQL SQL added here (`FOR UPDATE`, V4 and V5 migrations, session count) has only run against
      SQLite in tests.

### Blocked or not worth the risk
- [ ] Gradle dependency verification (`verification-metadata.xml`): `paper-api` is a `SNAPSHOT`, which has no
      stable checksum, and Gradle cannot record it. Fix: pin `paper-api` to a timestamped snapshot or trust
      the `io.papermc` group (which weakens the check).
- [ ] Bump `sqlite-jdbc` on Fabric from 3.49.1.0: pinned on purpose because newer versions broke Ledger.
      CVE-2025-6965 needs attacker-controlled SQL, which this plugin does not have.
- [ ] Session secret token instead of IP binding: a vanilla client has nowhere to keep one except cookie
      packets (1.20.5+), and it is not confirmed they are returned on a normal connection.
- [ ] Real premium detection on a direct offline server: ownership of a name cannot be proven without the
      Mojang handshake. Only the proxy-UUID option above is implemented.
- [ ] QR code for `/2fa enable`: needs a QR encoder plus map rendering and item handling that differ on Paper,
      Folia and Fabric. Players enter the key by hand or open the `otpauth://` link.
- [ ] Raising Paper handlers from `LOWEST` to `HIGHEST`: left at `LOWEST` on purpose so other plugins never see
      the event before it is cancelled. Trade-off: a plugin at a later priority could undo the cancel.

### Smaller gaps left open
- [ ] Duplicate-login guard on Fabric (needs a mixin on the login path).
- [ ] Remaining minor Paper events: arrow and XP pickup, book edit, held item change, entity targeting.
- [ ] Remaining Fabric packets: `SetCarriedItem`, `RenameItem`, `SelectTrade`, `EditBook`, `SignUpdate`, `PlayerCommand`.
- [ ] A new-address notice on session restore, and an alert to Discord or Telegram.
- [ ] Argon2 queue exhaustion (an IP limit before hashing), a failure counter that decays over time, and a
      bounded database queue.
- [ ] Redis: warn on cleartext `redis://`, and tell the operator when the fallback to local limits happens.
- [ ] Session restore ignores account lock or suspension, and only the newest persistent session is checked.
- [ ] Proxy forwarding is detected by a heuristic (shared local address), not by reading the proxy settings.
- [ ] Config and database file permissions, and plaintext webhook and bot tokens in `config.yml`.
- [ ] Audit events defined but never written (`LOGIN_FAILURE`, `ACCOUNT_LOCKED`, `ADMIN_ACTION`,
      `SESSION_REVOKED`); audit write errors are swallowed.
- [ ] Tests: mixin parity across the 17 Fabric targets, concurrent registration on a real PostgreSQL
      (Testcontainers), Paper and Fabric restriction parity.

### Ideas not started
- [ ] Bot protection before registration (connection rate, challenge).
- [ ] Password recovery through email or Discord.
- [ ] Prometheus metrics and audit export.
- [ ] Shared session token for lobby networks (one node checks the password, the others trust it).
- [ ] Automatic IP ban after repeated account lockouts (IP limits already lock an IP for a while).
