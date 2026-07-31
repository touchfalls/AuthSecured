# AuthSecured Architecture Overview

```text
Paper API / Bukkit
       │
       ▼
Presentation Layer (Listeners & Commands)
       │
       ▼
Application Layer (AuthService, LoginService, RegistrationService)
       │
       ├──► Ports (AccountRepository, PasswordHasher, RateLimitStore)
       │
       ▼
Infrastructure Layer (Argon2id, PostgreSQL/SQLite, Redis, Flyway)
```

1. **Domain Layer**: Contains immutable domain models (`Account`, `Session`, `AuthState`, `LoginAttempt`, `SecurityEvent`).
2. **Ports Layer**: Java interfaces defining storage and security contracts.
3. **Application Layer**: Business orchestration rules and rate-limiting enforcement.
4. **Infrastructure Layer**: Concrete persistence with HikariCP, Flyway, BouncyCastle, and Redis.
5. **Presentation Layer**: Paper listeners and command implementations.
