# AuthSecured — Production-Ready Minecraft Auth System (v1.0.2)

An enterprise-grade, high-performance authentication plugin for **Paper, Purpur, Spigot, and Leaf** Minecraft servers (1.20+ / Java 21+). Built with **Hexagonal Architecture**, **Argon2id** password hashing (OWASP benchmarked), **dual DB support** (SQLite/PostgreSQL + Flyway), and **Redis rate limiting**.

---

## 🌟 Key Features

- **Paper, Purpur, Spigot & Leaf Support**: Runs natively as a high-performance Spigot/Paper server plugin.
- **OWASP-Compliant Hashing**: Uses `Argon2id` via BouncyCastle to resist GPU/ASIC cracking.
- **Dual Database Core**: Automatic Flyway migrations for both local SQLite (`auth.db`) and PostgreSQL clusters.
- **Rate-Limiting Protection**: Account-based and IP-based brute force protection backed by Redis (or in-memory fallback).
- **Session Auto-Login**: Optional persistent player session bypass across reconnects.
- **Multi-Language i18n**: Out-of-the-box support for English (`en`), Russian (`ru`), Spanish (`es`), Italian (`it`), and French (`fr`).
- **Granular Restrictions**: Customizable action restrictions for unauthenticated players (movement, chat, block break/place, damage, inventory, item pickup/drop, hunger).
- **IP Hashing & Privacy**: Optional HMAC-SHA256 IP masking for GDPR compliance.
- **Admin Commands**: In-game password resets, account unlocks, unregistration, real-time session duration management, and reload tools.

---

## 🚀 Quick Start

### Build Requirements
- JDK 21+
- Bash & Git

### Building the Project

```bash
# Clone the repository
git clone https://github.com/touchfalls/authsecured.git
cd authsecured

# Run complete build and test suite
bash build_and_test.sh
```

The resulting JAR will be generated in `build/libs/AuthSecured-1.0.2.jar`.

## Commands & Permissions

| Command | Usage | Description | Permission |
|---------|-------|-------------|------------|
| `/register` | `/register <pass> <confirm>` | Register a new account | Default |
| `/login` | `/login <pass>` | Authenticate account | Default |
| `/changepassword` | `/changepassword <old> <new>` | Change account password | Default |
| `/logout` | `/logout` | Terminate session | Default |
| `/authstatus` | `/authstatus` | Check authentication status | Default |
| `/authadmin` | `/authadmin <reload\|unregister\|unlock\|resetpassword\|session>` | Administrative commands | `authsecured.admin` |

## Building from Source

```bash
# Build shaded plugin JAR
./gradlew shadowJar
```

The resulting JAR will be generated in `build/libs/AuthSecured-1.0.2.jar`.

## License
[MIT License](LICENSE)
