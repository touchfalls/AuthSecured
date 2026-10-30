# AuthSecured 1.2.7

AuthSecured is one authentication codebase delivered as either a server plugin
or a Fabric server mod. It supports Minecraft 1.21 through the current stable
release, 26.3.

## Platforms

- Paper, Purpur, Folia, Spigot, and Leaf: install `authsecured-paper-1.2.7.jar`
  in `plugins/`.
- Fabric: install the JAR whose `+mc...` suffix exactly matches the Minecraft
  version in `mods/`, together with the matching Fabric API release.
- Minecraft 1.21.x requires Java 21. Minecraft 26.x requires Java 25 or newer.

The complete target list and Fabric API mapping are documented in
[SUPPORTED_VERSIONS.md](SUPPORTED_VERSIONS.md).

## Features

- Argon2id password hashing through Bouncy Castle.
- SQLite by default, with PostgreSQL support and HikariCP connection pooling.
- Account and IP rate limiting through Redis or an in-memory fallback.
- Optional reconnect sessions with persistent/non-persistent storage and IP
  verification, optionally kept alive while the player stays online so a
  reconnect or proxy server switch does not ask for the password again.
- English, Russian, Spanish, Italian, and French messages.
- Pre-authentication restrictions for movement, chat, commands, inventory,
  interaction, block actions, combat, item pickup/drop, and hunger.
- Always-on redaction of authentication command arguments from Fabric command
  diagnostics and debug exception logs.
- A Paper log filter blocks raw login, registration, and password-change
  commands before formatting, while event interception protects later dispatch.
- Configurable authentication deadline with automatic kick.
- Runtime status, diagnostics, granular admin permissions, and audit viewing.
- Secure temporary password reset that forces the player to choose a permanent
  password before authentication completes.
- Optional privacy-safe failed-login alerts through Discord webhooks or a
  Telegram bot, with per-player cooldown.
- Shared domain and application services across Paper and Fabric, with only the
  server API adapters kept platform-specific.

## Build

Requirements: Bash and a JDK capable of running Gradle 9.5.1. Gradle toolchains
select Java 21 for Minecraft 1.21.x and Java 25-compatible bytecode for 26.x.

```bash
./build_and_test.sh
```

This runs every unit/integration test and builds:

- `build/libs/authsecured-paper-1.2.7.jar`
- `build/libs/authsecured-fabric-1.2.7+mc26.3.jar`

Build every supported Fabric target:

```bash
./build_fabric_matrix.sh
```

GitHub Actions runs the same tests and builds for every push and pull request,
and builds every Fabric target listed in `build_fabric_matrix.sh` in parallel.

Build one Fabric target:

```bash
./gradlew :platform-fabric:clean :platform-fabric:build -Pminecraft_version=1.21.11
```

## Installation

1. Stop the server.
2. Copy the correct artifact to `plugins/` (Paper-compatible server) or `mods/`
   (Fabric server). Fabric also requires Fabric API for the exact game version.
3. Start once to generate `config/authsecured/` on Fabric or
   `plugins/AuthSecured/` on Paper.
4. Set `AUTHSECURED_IP_SECRET` to a long random secret and restart. Authentication
   stays blocked until this secret is set. Changing it later invalidates
   IP-bound sessions and rate-limit identities. Set database and Redis
   environment variables when those integrations are enabled.
5. Review online-mode bypass and all pre-authentication restrictions before
   exposing the server publicly.

Passwords are accepted as command arguments because Minecraft has no native
masked server-command input. AuthSecured never logs those values. Fabric redacts
authentication arguments before diagnostics. Paper blocks its built-in raw
command record before console formatting and also redacts the later command
event. `/authadmin resetpassword` does not accept a password argument at all.
Its temporary password uses only safe letters and digits. An in-game
administrator can copy it by clicking the highlighted password with the left
mouse button.
Players should still avoid reusing a password from any other service.

Do not install both artifacts on the same server. They contain the same core but
target different server APIs.

## Commands

| Command | Usage | Permission |
| --- | --- | --- |
| `/register` | `/register <password> <confirmation>` | everyone |
| `/login` | `/login <password>` | everyone |
| `/changepassword` | `/changepassword <old> <new>` | everyone |
| `/logout` | `/logout` | everyone |
| `/authstatus` | `/authstatus` | everyone |
| `/sessions` | `/sessions [revoke]` shows or ends your saved sessions | everyone |
| `/2fa` | `/2fa enable`, `/2fa confirm <code>`, `/2fa disable <code>`, `/2fa status`; after `/login`, `/2fa <code>` finishes the login | everyone |
| `/authadmin status` | show service and database state | `authsecured.admin.status` / level 2 |
| `/authadmin doctor` | run configuration diagnostics | `authsecured.admin.doctor` / level 3 |
| `/authadmin reload` | reload runtime configuration | `authsecured.admin.reload` / level 3 |
| `/authadmin session gettimeout` | show the active session timeout | `authsecured.admin.session` / level 3 |
| `/authadmin session settimeout <seconds>` | change timeout for new sessions immediately | `authsecured.admin.session` / level 3 |
| `/authadmin audit [limit]` | show recent privacy-safe audit records | `authsecured.admin.audit` / level 3 |
| `/authadmin audit player <name> [limit]` | filter audit records by account | `authsecured.admin.audit` / level 3 |
| `/authadmin unlock <player>` | unlock an account | `authsecured.admin.unlock` / level 3 |
| `/authadmin unregister <player>` | delete an account | `authsecured.admin.unregister` / level 4 |
| `/authadmin resetpassword <player>` | generate an expiring temporary password | `authsecured.admin.resetpassword` / level 4 |

Paper permission plugins may grant one operation or the parent
`authsecured.admin` permission. Fabric permission levels are configurable per
operation in `admin.fabric-permission-levels`.

Failed-login alerts are disabled by default. Enable the global switch and the
desired `notifications.discord.enabled` or `notifications.telegram.enabled`
channel. Credentials can be entered directly in `config.yml`, or more safely
through `AUTHSECURED_DISCORD_WEBHOOK`, `AUTHSECURED_TELEGRAM_BOT_TOKEN`, and
`AUTHSECURED_TELEGRAM_CHAT_ID`. Restart after changing notification endpoints.

Audit and expired-session cleanup runs off the server thread at the configured
interval. `logging.audit.retention-days` controls how long audit rows remain.
For proxy networks using shared Redis limits, set `redis.required: true` so
authentication fails closed if Redis becomes unavailable.

## Configuration and deployment

The generated configuration contains database, Redis, password policy, session,
online-mode bypass, localization, and restriction settings. See
[docs/deployment.md](docs/deployment.md) for production notes and
[docs/architecture.md](docs/architecture.md) for module boundaries.

## License

[MIT](LICENSE)

Author: touchfalls.
