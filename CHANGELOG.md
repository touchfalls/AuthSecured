# Changelog

All notable changes to this project will be documented in this file.

## [1.0.1] - 2026-08-01

### Added
- Fabric Dedicated Server support (`fabric.mod.json` and `AuthSecuredFabricMod` entrypoint) for multi-platform deployment on Fabric, Paper, and Purpur servers.
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
- Hexagonal Architecture & Clean Separation.
- Argon2id password hashing with BouncyCastle provider.
- Dual database support: PostgreSQL & SQLite with Flyway migrations.
- Rate limiting for per-account & per-IP abuse prevention.
- Pre-auth restriction system for Paper & Purpur.
- Integration test suite for SQLite and unit test suite.
