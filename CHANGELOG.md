# Changelog

All notable changes to this project will be documented in this file.

## [1.2.7] - 2026-10-26

### Added
- Paper: Folia support. Player-bound work runs on the region that owns the player, global work on
  the global scheduler. Thanks to @nicki41.
- `session.extend-while-online` (default `false`): keeps an online player's session alive, so the
  session timeout counts from when they leave instead of from login. Applied by
  `/authadmin reload`. Thanks to @nicki41.

### Security
- The per-IP account limit is checked in the same step as the insert, so simultaneous
  registrations can no longer exceed `max-accounts-per-ip` (SQLite lock; PostgreSQL per-IP
  advisory lock, shared across servers on one database). A full IP is reported as `RATE_LIMITED`.
- `/authadmin doctor` treats only numeric `127.x.x.x` addresses as loopback, so a host such as
  `127.db.example.com` still gets the TLS warning.

### Fixed
- Audit and login records queued at shutdown are written before the database pool closes.

### Changed
- Paper: the allowed-commands list is rebuilt only when the configured list changes.
- Removed unused `AuthService` methods that skipped the online logout.

## [1.2.6] - 2026-10-24

### Added
- `/authadmin doctor` reports three more risky settings: IP-unbound sessions on an offline-mode
  server, Argon2 parameters below the OWASP minimum, and unencrypted PostgreSQL to a remote
  host (translated into all bundled languages). Thanks to @nicki41.

### Security
- `/authadmin resetpassword` and `/authadmin unregister` now also end the in-game authentication
  of an online account owner (Paper and Fabric), so a compromised account loses access at once.
  Thanks to @nicki41.
- Paper: password-command aliases that another plugin already owns (for example `/l`) stay
  protected: the console-log redaction and the pre-login interception no longer miss them.
  Thanks to @nicki41.
- Paper: players who have not logged in only see the commands in `restrictions.allowed-commands`
  in their command list and tab completion; the list is re-sent on login and logout. Thanks to
  @nicki41.

### Changed
- Passwords hashed with outdated Argon2 settings are re-hashed with the current ones after a
  successful login. Thanks to @nicki41.
- GitHub Actions now builds and tests the Paper jar and every Fabric target on each push and
  pull request. Thanks to @nicki41.
- The Argon2 and database worker threads now have descriptive names (`authsecured-argon2-N`,
  `authsecured-database-N`) for thread dumps and profilers; Argon2 workers are daemon threads.
  Thanks to @nicki41.

### Contributors
- Thanks to [@nicki41](https://github.com/nicki41) for the doctor checks (#8), hiding commands
  before login on Paper (#9), the worker thread names (#10), re-hashing on login (#7), the
  command alias protection (#4), ending authentication on admin reset/unregister (#3) and the
  GitHub Actions build (#5).

## [1.2.5] - 2026-10-24

### Fixed
- Fabric: the bundled SQLite driver is now 3.49.1.0 (was 3.53.4.0). It cannot be relocated and
  takes precedence over other mods' copies; 3.53.x made Ledger crash at startup with
  `ALTER TABLE players MODIFY COLUMN ...`.

## [1.2.4] - 2026-10-23

### Security
- The in-memory rate-limit store evicts the lowest-count entry at capacity, so a flood of
  cheap fake keys can no longer wipe a real account's failed-attempt count.
- The per-IP account registration cap is read from the `accounts` table (a new
  `registration_ip_hash` column, backfilled from existing audit history) instead of
  `security_events`, so it keeps working with audit logging disabled or past retention.
- A lock that has expired resets the failed-attempt counter to 1 instead of adding to the
  stale total, so one more wrong guess right after expiry no longer re-locks the account.
- Password-change, temporary-password and unlock writes touch only their own columns
  instead of overwriting the whole account row, so they can no longer undo a failed-attempt
  counter or lock written concurrently by a slower request.
- IPv6 addresses are rounded to their /64 network prefix before hashing, closing an IP
  rate-limit and registration-cap bypass through address rotation within one network.
- Fabric: an asynchronous login/register/password-change result is dropped if the player
  reconnected under a new connection while it was still computing.
- Redis's increment and its expiry now run as one atomic script; a dropped connection
  between the two could previously leave a counter with no expiry that never reset.
- `/authadmin unlock` and issuing a temporary password also clear the rate-limit lock, so the
  action takes effect immediately instead of leaving the player rate-limited.
- `/authadmin resetpassword` refuses to run from the console or RCON, which would otherwise
  write the password to the server log.
- Login-failure webhook alerts no longer fire for already-rate-limited attempts, and the
  alert cooldown table evicts its oldest entry instead of permanently dropping new names
  once full.
- `/logout` revokes every active session for the account, not only the most recently created
  one.
- `doctor` warns when the IP hashing secret is a per-server file but Redis or PostgreSQL is
  enabled, since every server sharing that backend then needs the same secret.

### Fixed
- Jackson (pulled in transitively by Flyway) is now relocated in both shaded jars; the build
  fails if it, or any JSON library, is not relocated.
- The password-hashing queue is now bounded; a flood of login attempts is rejected instead of
  growing the queue, and the passwords it holds, without limit.
- Fabric: pre-login commands sent as a Brigadier password argument now accept any character
  without quoting, matching Paper; the two-argument commands split the same way Paper's
  command parsing already does.
- A failed database migration now closes the connection pool instead of leaking it.

### Changed
- Paper's `/login`, `/register`, `/changepassword`, `/logout` and `/authstatus` commands now
  delegate to the same `UnifiedCommandExecutor` Fabric uses, instead of duplicating the
  authentication and state-management logic.

## [1.2.3] - 2026-10-23

### Added
- Fabric support for Minecraft 26.3, mapped to Fabric API 0.164.0+26.3.

### Security
- `/changepassword` now uses the same checks as `/login`: IP and account rate limits, the
  failed-attempt counter, account locks and temporary-password expiry. Before login the command
  is only accepted after a temporary password was accepted; Paper also enforces
  `restrictions.allowed-commands` for password commands.
- Fabric blocks pre-login commands sent through the signed-command packet (`/msg`, `/me`, or any
  command from a modified client) and checks every command again in `Commands.performCommand`.
  The command injections are now required, so a renamed handler fails loading instead of
  silently allowing commands.
- A locked account answers `ACCOUNT_LOCKED` for correct and wrong passwords alike, so the lock no
  longer confirms a guessed password.
- The in-memory rate-limit store evicts the soonest-expiring entry at capacity instead of
  reporting every key as locked.
- Paper stays fail-closed when startup throws an `Error` or the log filter cannot be installed.

### Fixed
- PostgreSQL security events are bound as JSONB (`Types.OTHER`) and were previously rejected;
  plain-text audit metadata is wrapped into JSON.
- Spigot: the restriction listener used the Paper-only `PlayerAttemptPickupItemEvent` and the
  temporary-password message used Paper-only Adventure; both now use Bukkit/BungeeCord APIs.

### Changed
- Current maximum supported Minecraft release is now 26.3.
- Unauthenticated players cannot move at all: Paper sets walk/fly speed to zero, undoes every
  position change (including jumps), blocks vehicles, gliding and pearl/portal/spectator
  teleports; Fabric pulls the client back to its server position and blocks vehicle movement,
  spectator teleports, creative-slot edits and hand swapping.

## [1.2.2] - 2026-09-16

### Security
- Removed the public fallback IP hashing key. A private `AUTHSECURED_IP_SECRET` is now required for startup.
- Unknown IP addresses no longer share a synthetic identity or create IP-bound reconnect sessions.
- Stored Argon2id cost and output lengths are bounded before verification; malformed hashes produce safe diagnostics.
- A locked account without a lock expiry remains locked until an administrator resolves it.
- Unexpected authentication failures log exception types without credential-bearing exception messages.
- Redis can be marked required, including at runtime; optional fallback is reported as degraded.
- The in-memory rate-limit store has a hard capacity and amortized expiry cleanup.
- Discord webhook URLs are restricted to official HTTPS endpoints.

### Reliability and maintenance
- SQLite enforces foreign keys on every pooled connection and tracks schema version 2.
- SQLite migration scripts are executed with SQLite's native SQL parser, including triggers and quoted semicolons. Legacy partial schemas recreate missing V1 tables before their version is advanced.
- Paper and Fabric trigger periodic off-thread cleanup of expired sessions and old audit records.
- Audit retention and cleanup interval are configurable and validated.

### Packaging
- Moved project packages to `io.github.touchfalls.authsecured`.
- Relocated bundled Gson and org.json; retained the original SQLite JNI package.
- Minimized shaded artifacts while preserving reflective JDBC and Flyway dependencies.
- Updated vulnerable Bouncy Castle, PostgreSQL JDBC, and Jackson dependencies, plus SQLite, HikariCP, Flyway, Jedis, SnakeYAML, Gson, and org.json.
- Added Fabric metadata and bytecode consistency tests for exact Minecraft targets.

## [1.2.1] - 2026-09-15

### Security
- Existing accounts always complete Argon2 verification before lock and
  temporary-password expiry responses, reducing account-state enumeration.
- Failed login counters are updated atomically in SQLite and PostgreSQL.
- Registration maps database uniqueness conflicts to an already-registered
  result, closing the concurrent registration race.
- Password changes reject the current password using a constant-time comparison.
- Temporary passwords contain only unambiguous letters and digits.
- Redis calls use a dedicated executor with timeout fallback instead of using
  the shared asynchronous pool.

### Added
- Temporary passwords are clickable and copyable for in-game administrators on
  Paper and Fabric.
- Discord and Telegram settings now include explicit channel switches, direct
  values, environment-variable values, and explanatory comments.
- Existing configuration files without a notifications section are migrated
  atomically on startup.

### Fixed
- Non-persistent mode clears stored database sessions before accepting session
  operations, preventing stale persistent sessions from surviving a mode change.
- Invalid but syntactically correct configuration values no longer replace the
  active configuration during `/authadmin reload`.
- Paper remains fail-closed and denies new logins if initialization fails.
- Paper asynchronous join callbacks ignore disconnected or replaced players.
- Database session and audit access consistently closes JDBC resources.

### Changed
- In-memory rate-limit counters use immutable values and atomic map updates.
- All new player and administrator messages are translated in English, Russian,
  Spanish, Italian, and French.

## [1.2.0] - 2026-09-15

### Added
- Configurable authentication deadline with automatic kick on Paper and Fabric.
- `/authadmin status`, `/authadmin doctor`, runtime session timeout management,
  audit viewing, tab completion, and operation-specific permissions.
- Expiring temporary password reset that forces an immediate permanent password
  change and revokes existing sessions.
- Optional privacy-safe Discord webhook and Telegram bot alerts for failed
  logins, with per-player cooldown.
- Backward-compatible SQLite and PostgreSQL account schema migrations.

### Fixed
- `/authadmin session settimeout` now changes the live timeout used for newly
  created sessions.
- Paper blocks its built-in raw command log before formatting and also replaces
  password-bearing command event text before normal dispatch.

### Changed
- Shared platform-neutral admin operations reduce duplicated Paper and Fabric
  command logic.
- Administrative errors and audit views no longer expose raw exception details,
  IP addresses, credentials, webhook URLs, or bot tokens.
- Infrastructure failures hide JDBC, Redis, YAML, and webhook exception details
  so credentials cannot leak through diagnostic messages.

## [1.1.1] - 2026-08-26

### Added
- Always-on redaction of password arguments from Fabric command diagnostics,
  profiler labels, and debug exception logs.
- Validation bounds for security, database, Redis, rate-limit, and session
  numeric settings, plus PostgreSQL `ssl-mode` support.

### Fixed
- Fabric now stays fail-closed when core initialization fails, blocks flight
  according to `restrictions.allow-flight`, and ignores stale asynchronous join
  callbacks after a disconnect or rapid reconnect.
- Invalid YAML reloads retain the last valid configuration snapshot.

### Changed
- Configuration lookups now use an immutable flattened snapshot, reducing work
  in high-frequency movement and packet restriction checks.
- `config.yml` now documents every active option, accepted values, units,
  reload behavior, environment variables, and security-sensitive defaults.

## [1.1.0] - 2026-08-17

### Added
- Restored Fabric as a production platform backed by the same core and database
  modules as the Paper-compatible plugin.
- Exact Fabric builds for every Minecraft release from 1.21 through 26.2, with
  version-specific mappings, Fabric API dependencies, metadata, and Java levels.
- Brigadier authentication/admin commands, lifecycle integration, reconnect
  handling, and pre-authentication restrictions for Fabric dedicated servers.
- A full Fabric version-matrix build script and shared `platform-common`
  dependency graph.

### Fixed
- Paper now uses the same restriction manager instance for listeners, commands,
  and the core platform adapter.
- Registration confirmation, localized command result keys, logout revocation,
  session IP verification, and non-persistent session behavior.
- SQLite native loading in shaded Paper and Fabric release artifacts.
- Database integration tests are now executed by the canonical Gradle test task.

### Changed
- Minimum supported Minecraft version is 1.21; current maximum is 26.2.
- Build moved to Gradle 9.5.1, Shadow 9.6.1, and Fabric Loom 1.17.19.
- Shared platform-independent bootstrapping moved out of the Paper module.

## [1.0.3] - 2026-08-01

### Added
- Cleaned up `.vscode/settings.json` and enabled automatic Gradle project synchronization (`java.import.gradle.enabled`) for Java IDEs.

### Removed
- Complete removal of Fabric module (`platform-fabric`), Fabric Loader dependencies, Fabric API listeners, Brigadier registrations, and Mixin configurations (architectural decision to maintain Fabric as a standalone repository).
- Removed legacy root `.project` and `.classpath` files that caused non-project file warnings in IDEs.

### Changed
- Transitioned repository build system to a Paper/Purpur-focused architecture (`:core`, `:database`, `:platform-paper`).
- Updated `build_and_test.sh` to compile core/database/paper modules and generate single `authsecured-paper-1.0.3.jar` artifact.
- Cleaned up docstrings across core ports [AuthPlatform], [PlatformLogger], [PlayerRestrictionAdapter]

## [1.0.2] - 2026-08-01

### Added
- Specialized Paper, Purpur, Spigot, and Leaf high-performance Minecraft server architecture.
- Multi-language localization system with 5 language bundles: English (`messages_en.yml`), Russian (`messages_ru.yml`), Spanish (`messages_es.yml`), Italian (`messages_it.yml`), and French (`messages_fr.yml`).
- Chat message color customization (`&0`-`&f`, `&l`, `&o`, etc. -> `§`), customizable command usage labels, and automatic English fallback.
- Fully customizable session configuration (`session.timeout-seconds`, `session.enabled`, `session.verify-ip`, `session.persistent`).
- Admin session management subcommands (`/authadmin session gettimeout` and `/authadmin session settimeout <seconds>`).
- Granular unauthenticated player action restrictions in `config.yml` (`freeze-player`, `allow-flight`, `block-chat`, `block-commands`, `allowed-commands`, `block-inventory`, `block-interactions`, `block-block-break`, `block-block-place`, `block-damage-taken`, `block-damage-dealt`, `block-item-drop`, `block-item-pickup`, `block-hunger`).

### Fixed
- Added exception handling for asynchronous operations, listeners, and administrative commands.
- Safe password character array clearing in memory on both success and error execution paths.

### Changed
- Updated release version to `1.0.2`.

## [1.0.1] - 2026-08-01

### Added
- Standard `.gitignore` configuring ignores for build directories (`.gradle/`, `build/`), IDE workspace settings (`.idea/`, `*.iml`), system files, and local SQLite runtime databases.

### Fixed
- Fixed SQL column mapping mismatch (`server_id` vs `serverId`) in `DatabaseSessionRepository`.
- Fixed `RedisRateLimitStore` initialization in `DependencyContainer` to instantiate `JedisPool` when Redis is enabled, and gracefully close connection pool on shutdown.
- Fixed session invalidation in `/logout` command to revoke active database sessions.
- Fixed potential `NullPointerException` race condition in `MemoryRateLimitStore` and added expiration key cleanup to prevent memory leaks.
- Fixed `countAccountsByIpHash` in SQLite & PostgreSQL repositories to accurately count registered accounts from `security_events` (`REGISTER_SUCCESS`).
- Fixed potential `NullPointerException` when calling `player.getAddress()` across commands by adding safe IP fallback (`0.0.0.0`).
- Fixed `/authadmin reload` behavior to reload YAML configurations and refresh active settings.

### Changed
- Removed duplicate Groovy build files (`build.gradle`, `settings.gradle`) in favor of clean Gradle Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`).
- Updated release version to `1.0.1`.

## [1.0.0] - 2026-08-01

### Added
- Initial release of AuthSecured plugin.
- Separated domain, application, persistence, and server-platform modules.
- Argon2id password hashing with BouncyCastle provider.
- Dual database support: PostgreSQL & SQLite with Flyway migrations.
- Rate limiting for per-account & per-IP abuse prevention.
- Pre-auth restriction system for Paper & Purpur.
- Integration test suite for SQLite and unit test suite.
