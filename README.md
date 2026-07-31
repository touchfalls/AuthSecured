# AuthSecured — Production-Ready Minecraft Auth System (v1.0.1)

**AuthSecured** is a modern, enterprise-grade authentication plugin and server mod designed specifically for **Fabric**, **Paper**, and **Purpur** Minecraft servers (1.20.4+ / 1.21+ / Java 21+).

## Key Features

- 🔒 **Argon2id Password Hashing**: Memory-hard password hashing with unique random salts and constant-time password comparisons.
- 🌐 **Multi-Platform Support**: Works seamlessly on **Fabric Dedicated Server**, **Paper**, and **Purpur** servers.
- ⚡ **Zero Main Thread Lockup**: Password hashing, database calls, and network requests are executed off the server main loop via dedicated thread pools.
- 🛡️ **Hexagonal Architecture**: Clean separation between server platforms, application logic, and infrastructure adapters (PostgreSQL, SQLite, Redis).
- 🗄️ **Dual Database & Migrations**: Full PostgreSQL & SQLite support managed via Flyway / DB initializers.
- 🚫 **Pre-Auth Isolation**: Restricts movement, chat, commands, block break/place, interactions, inventory access, and damage prior to authentication.
- ⚡ **Multi-Layer Rate Limiting**: Independent Account-based and IP-based rate limiting with progressive throttling and account locking.
- 🕵️ **Privacy-Preserving Audit Log**: IP addresses are stored hashed via HMAC-SHA256 with a server secret.

## Commands & Permissions

| Command | Usage | Description | Permission |
|---------|-------|-------------|------------|
| `/register` | `/register <pass> <confirm>` | Register a new account | Default |
| `/login` | `/login <pass>` | Authenticate account | Default |
| `/changepassword` | `/changepassword <old> <new>` | Change account password | Default |
| `/logout` | `/logout` | Terminate session | Default |
| `/authstatus` | `/authstatus` | Check authentication status | Default |
| `/authadmin` | `/authadmin <reload\|unregister\|unlock\|resetpassword>` | Administrative commands | `authsecured.admin` |

## Building from Source

```bash
# Build shaded plugin JAR
./gradlew shadowJar
```

The resulting JAR will be generated in `build/libs/AuthSecured-1.0.1.jar`.

## License
[MIT License](LICENSE)
