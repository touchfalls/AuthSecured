# Changelog

All notable changes to this project will be documented in this file.

## [1.0.3] - 2026-08-01

### Added
- Complete Gradle multi-module monorepo architecture (`:core`, `:database`, `:platform-paper`, `:platform-fabric`).
- Native Fabric Dedicated Server support (`authsecured-fabric-1.0.3.jar`) alongside Paper/Purpur (`authsecured-paper-1.0.3.jar`).
- Fabric Brigadier command registrations for `/register`, `/login`, `/changepassword`, `/logout`, and `/authstatus`.
- Fabric API `ServerMessageEvents.ALLOW_CHAT_MESSAGE` event listener to block unauthenticated player chat.

### Fixed
- Fixed Fabric Loader startup crash (`ClassNotFoundException: ServerPlayNetworkHandlerMixin`) by migrating unauthenticated chat blocking to standard Fabric API events.
- Fixed `DependencyContainer` initialization on Fabric Dedicated Server to instantiate SQLite/PostgreSQL database pools, Argon2id hashing, and rate limiting engines upon server start.
- Fixed dual `"main"` and `"server"` entrypoint resolutions in `fabric.mod.json`.

### Changed
- Refactored core business logic into pure Java `:core` module with 0 Minecraft API dependencies.
- Updated release version to `1.0.3`.

## [1.0.2] - 2026-08-01

### Added
- Specialized Paper, Purpur, Spigot, and Leaf high-performance Minecraft server architecture.
- Multi-language localization system with 5 language bundles: English (`messages_en.yml`), Russian (`messages_ru.yml`), Spanish (`messages_es.yml`), Italian (`messages_it.yml`), and French (`messages_fr.yml`).
- Chat message color customization (`&0`-`&f`, `&l`, `&o`, etc. -> `§`), customizable command usage labels, and automatic English fallback.
- Fully customizable session configuration (`session.timeout-seconds`, `session.enabled`, `session.verify-ip`, `session.persistent`).
- Admin session management subcommands (`/authadmin session gettimeout` and `/authadmin session settimeout <seconds>`).
- Granular unauthenticated player action restrictions in `config.yml` (`freeze-player`, `allow-flight`, `block-chat`, `block-commands`, `allowed-commands`, `block-inventory`, `block-interactions`, `block-block-break`, `block-block-place`, `block-damage-taken`, `block-damage-dealt`, `block-item-drop`, `block-item-pickup`, `block-hunger`).

### Fixed
- Robust exception handling across all asynchronous futures, event listeners, and administrative command handlers.
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
- Hexagonal Architecture & Clean Separation.
- Argon2id password hashing with BouncyCastle provider.
- Dual database support: PostgreSQL & SQLite with Flyway migrations.
- Rate limiting for per-account & per-IP abuse prevention.
- Pre-auth restriction system for Paper & Purpur.
- Integration test suite for SQLite and unit test suite.
