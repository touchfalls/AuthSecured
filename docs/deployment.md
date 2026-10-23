# Deployment Guide

## Production Network Topology

```text
Internet ──► Proxy (optional) ──► Paper/Purpur plugin or Fabric server mod
                                               │
                                               └──► SQLite or PostgreSQL + optional Redis
```

## Platform installation

- Paper-compatible server: copy `authsecured-paper-<version>.jar` to `plugins/`.
- Fabric server: copy the exactly matching `authsecured-fabric-<version>+mc<minecraft_version>.jar`
  and Fabric API to `mods/`. See [SUPPORTED_VERSIONS.md](../SUPPORTED_VERSIONS.md) for the
  current `<version>` and the Fabric API release each Minecraft version needs.
- Use Java 21 for Minecraft 1.21.x and Java 25 or newer for Minecraft 26.x.
- Never load both AuthSecured artifacts in one server process.

## Recommended Environment Variables
- `AUTHSECURED_DB_PASSWORD`: PostgreSQL database password
- `AUTHSECURED_IP_SECRET`: long random server secret for IP HMAC hashing; keep it
  stable or existing IP-bound sessions will be invalidated
- `AUTHSECURED_REDIS_URI`: Optional Redis connection string
- `AUTHSECURED_DISCORD_WEBHOOK`: Optional Discord webhook for failed-login alerts
- `AUTHSECURED_TELEGRAM_BOT_TOKEN`: Optional Telegram bot token
- `AUTHSECURED_TELEGRAM_CHAT_ID`: Telegram destination used with the bot token

Keep every credential in the server environment. Do not paste database
passwords, webhook URLs, or Telegram bot tokens directly into `config.yml`.
Enable alerts with `notifications.login-failures.enabled` and restart after
changing notification endpoints.

For proxied offline-mode backends, restrict direct backend access and configure
the proxy's secure player forwarding. Otherwise UUIDs and client addresses are
not trustworthy. Back up the database before upgrades and test the selected
platform artifact on a staging server first.

To let players log in once and then move between backend servers of a proxy
network, give every backend the same PostgreSQL database and the same
`AUTHSECURED_IP_SECRET`, keep `session.enabled` and `session.persistent` on, and
set `session.extend-while-online: true`. A server switch is a new connection to
another backend, which then accepts the shared session; with the extension
enabled that session stays valid however long the player has been online, and
`session.timeout-seconds` counts from when they leave the network.
