# AuthSecured

AuthSecured adds password authentication to Minecraft dedicated servers. The
same authentication core is available as a Paper-compatible plugin and as a
Fabric server mod.

## Supported servers

- Minecraft 1.21 through 1.21.11
- Minecraft 26.1 through 26.3
- Paper, Purpur, Folia, Spigot, Leaf, and Fabric

Fabric builds target an exact Minecraft version. Download the file whose
`+mc...` suffix matches the server and install the matching Fabric API version.
Paper-compatible servers use one plugin JAR across the supported range.

## Features

- Argon2id password hashing
- SQLite and PostgreSQL storage
- Redis or local in-memory rate limiting
- Optional reconnect sessions with IP verification
- Configurable restrictions before authentication
- English, Russian, Spanish, Italian, and French messages
- Authentication timeout with automatic kick
- Status, doctor, audit, tab completion, and granular admin permissions
- Expiring temporary password reset with mandatory permanent password change
- Optional Discord webhook and Telegram bot failed-login alerts

## Installation

### Paper, Purpur, Folia, Spigot, or Leaf

Place `authsecured-paper-1.2.3.jar` in `plugins/` and restart the server.

### Fabric

Place the exact `authsecured-fabric-1.2.3+mc<version>.jar` and the matching
Fabric API in `mods/`, then restart the server.

Minecraft 1.21.x uses Java 21. Minecraft 26.x uses Java 25 or newer. Do not load
the Paper and Fabric artifacts together.

Set a private `AUTHSECURED_IP_SECRET` before accepting players and review the
generated database, online-mode, session, and restriction settings.

Author: touchfalls.

License: MIT.
