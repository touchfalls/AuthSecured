# Minecraft Auth Plugin — Architecture & Master Specification

> **Цель:** спроектировать production-ready плагин авторизации для Minecraft Java Edition на Paper/Purpur с максимальной практической защитой без 2FA.
>
> **Целевая серверная платформа:** Paper / Purpur.
>
> **Целевая версия:** актуальная ветка Paper API `26.2` на момент составления спецификации. Версию API и Java следует держать параметризованными в Gradle, чтобы обновление Minecraft не требовало переписывания архитектуры.
>
> **Главный принцип:** использовать Paper API вместо NMS там, где это возможно. NMS не должен быть частью auth-core.

---

## 1. Что должен делать плагин

Плагин предоставляет безопасную регистрацию и авторизацию игроков на сервере:

- `/register <password> <password>`
- `/login <password>`
- `/changepassword <oldPassword> <newPassword>`
- `/logout`
- `/authstatus`
- административные команды:
  - `/authadmin unregister <player>`
  - `/authadmin resetpassword <player>`
  - `/authadmin sessions <player>`
  - `/authadmin reload`
  - `/authadmin lock <player>`
  - `/authadmin unlock <player>`

### До авторизации игрок должен быть максимально ограничен

Игрок после входа:

- не может двигаться;
- не может вращаться/взаимодействовать с миром;
- не может ломать/ставить блоки;
- не может атаковать;
- не может открывать контейнеры;
- не может подбирать/выбрасывать предметы;
- не может использовать предметы;
- не может менять слот;
- не может писать обычные сообщения;
- не может использовать обычные команды;
- не может выполнять plugin-message действия;
- не получает игровой контент, который не требуется для авторизации;
- не может телепортироваться;
- не может использовать транспорт;
- не может взаимодействовать с сущностями;
- не может использовать команды других плагинов.

Разрешённый минимум:

- `/login`
- `/register`
- `/changepassword`
- `/help` для auth-команд, если включено конфигурацией.

---

# 2. Модель угроз

Плагин должен проектироваться не только против неправильного пароля, но и против:

1. brute-force паролей;
2. credential stuffing;
3. массовых попыток регистрации;
4. username spoofing;
5. IP abuse;
6. ботов;
7. packet/event spam;
8. session hijacking;
9. утечки базы;
10. утечки логов;
11. timing attacks;
12. SQL injection;
13. race conditions;
14. двойной регистрации;
15. повторной авторизации;
16. обхода ограничений через команды других плагинов;
17. обхода через plugin messaging;
18. обхода через телепорты;
19. обхода через reconnect;
20. обхода через proxy;
21. утечки паролей через console/logging;
22. DoS через дорогую password hashing операцию;
23. некорректного поведения при выключении/перезапуске сервера.

### Важная граница

Никакой auth-плагин не сможет компенсировать небезопасную сетевую архитектуру сервера.

Если используется Velocity:

```text
Internet
   |
   v
Velocity Proxy
   |
   | encrypted/private network
   v
Paper/Purpur Backend
   |
   v
Auth Plugin
```

Backend-серверы должны быть недоступны напрямую из интернета.

---

# 3. Архитектура

Рекомендуемая архитектура:

```text
                    ┌─────────────────────┐
                    │   Minecraft Client  │
                    └──────────┬──────────┘
                               │
                               v
                    ┌─────────────────────┐
                    │ Paper / Purpur      │
                    │ Server              │
                    └──────────┬──────────┘
                               │
                    ┌──────────v──────────┐
                    │ Auth Plugin         │
                    │                     │
                    │ ┌─────────────────┐ │
                    │ │ Event Layer     │ │
                    │ └────────┬────────┘ │
                    │          v          │
                    │ ┌─────────────────┐ │
                    │ │ Auth State      │ │
                    │ │ Machine         │ │
                    │ └────────┬────────┘ │
                    │          v          │
                    │ ┌─────────────────┐ │
                    │ │ Auth Service    │ │
                    │ └───────┬─────────┘ │
                    │         │           │
                    │   ┌─────┴──────┐    │
                    │   v            v    │
                    │ Hashing      Rate   │
                    │ Service      Limit  │
                    │                │    │
                    │   ┌────────────v─┐  │
                    │   │ Repository   │  │
                    │   └──────┬──────┘  │
                    └──────────┼─────────┘
                               │
                 ┌─────────────┴─────────────┐
                 v                           v
          ┌──────────────┐           ┌──────────────┐
          │ PostgreSQL   │           │ Redis        │
          │ / SQLite     │           │ optional     │
          └──────────────┘           └──────────────┘
```

---

# 4. Главные архитектурные слои

## 4.1 Presentation / Bukkit-Paper layer

Отвечает только за интеграцию с Minecraft.

Содержит:

- listeners;
- commands;
- tab completion;
- player messaging;
- permissions;
- server lifecycle;
- scheduler adapters.

Этот слой не должен содержать SQL, password hashing и бизнес-логику.

---

## 4.2 Application layer

Главный слой бизнес-логики.

Примеры:

```text
AuthService
RegistrationService
LoginService
PasswordService
SessionService
AccountLockService
RateLimitService
PlayerRestrictionService
```

Он работает с интерфейсами:

```text
AccountRepository
AuditRepository
SessionRepository
RateLimitStore
PasswordHasher
```

Таким образом Paper API не протекает в core.

---

## 4.3 Domain layer

Содержит модели и правила:

```text
AuthState
Account
Session
LoginAttempt
RateLimitState
AccountStatus
AuthResult
SecurityEvent
```

Пример состояния игрока:

```text
CONNECTED
   |
   v
AUTH_REQUIRED
   |
   +---- register ----> AUTHENTICATED
   |
   +---- login -------> AUTHENTICATED
   |
   +---- timeout -----> KICKED
   |
   +---- too many failures -> LOCKED / KICKED
```

---

## 4.4 Infrastructure layer

Здесь находятся:

- PostgreSQL;
- SQLite;
- HikariCP;
- Flyway;
- Redis;
- Argon2id;
- logging;
- configuration;
- metrics;
- UUID/IP utilities.

---

# 5. Структура проекта

Рекомендуемая структура:

```text
auth-secured/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── README.md
├── LICENSE
├── SECURITY.md
├── CHANGELOG.md
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/authsecured/
│   │   │       │
│   │   │       ├── AuthSecuredPlugin.java
│   │   │       │
│   │   │       ├── bootstrap/
│   │   │       │   ├── PluginBootstrap.java
│   │   │       │   └── DependencyContainer.java
│   │   │       │
│   │   │       ├── domain/
│   │   │       │   ├── account/
│   │   │       │   │   ├── Account.java
│   │   │       │   │   ├── AccountStatus.java
│   │   │       │   │   └── AccountId.java
│   │   │       │   ├── auth/
│   │   │       │   │   ├── AuthState.java
│   │   │       │   │   ├── AuthResult.java
│   │   │       │   │   └── LoginAttempt.java
│   │   │       │   ├── session/
│   │   │       │   │   └── Session.java
│   │   │       │   └── security/
│   │   │           │       ├── SecurityEvent.java
│   │   │           │       └── SecurityEventType.java
│   │   │       │
│   │   │       ├── application/
│   │   │       │   ├── AuthService.java
│   │   │       │   ├── RegistrationService.java
│   │   │       │   ├── LoginService.java
│   │   │       │   ├── PasswordService.java
│   │   │       │   ├── SessionService.java
│   │   │       │   ├── RateLimitService.java
│   │   │       │   └── SecurityService.java
│   │   │       │
│   │   │       ├── ports/
│   │   │       │   ├── AccountRepository.java
│   │   │       │   ├── SessionRepository.java
│   │   │       │   ├── AuditRepository.java
│   │   │       │   ├── RateLimitStore.java
│   │   │       │   └── PasswordHasher.java
│   │   │       │
│   │   │       ├── infrastructure/
│   │   │       │   ├── database/
│   │   │       │   │   ├── DatabaseManager.java
│   │   │       │   │   ├── PostgresAccountRepository.java
│   │   │       │   │   ├── SqliteAccountRepository.java
│   │   │       │   │   └── migrations/
│   │   │       │   ├── security/
│   │   │       │   │   ├── Argon2PasswordHasher.java
│   │   │       │   │   ├── SecureRandomProvider.java
│   │   │       │   │   └── IpHashService.java
│   │   │       │   ├── redis/
│   │   │       │   │   └── RedisRateLimitStore.java
│   │   │       │   └── config/
│   │   │           │       └── ConfigManager.java
│   │   │       │
│   │   │       ├── paper/
│   │   │       │   ├── listener/
│   │   │       │   │   ├── PlayerConnectionListener.java
│   │   │       │   │   ├── PlayerAuthRestrictionListener.java
│   │   │       │   │   ├── PlayerCommandListener.java
│   │   │       │   │   └── PlayerInteractionListener.java
│   │   │       │   ├── command/
│   │   │       │   │   ├── LoginCommand.java
│   │   │       │   │   ├── RegisterCommand.java
│   │   │       │   │   ├── ChangePasswordCommand.java
│   │   │       │   │   └── AuthAdminCommand.java
│   │   │       │   └── player/
│   │   │       │       └── PlayerRestrictionManager.java
│   │   │       │
│   │   │       └── util/
│   │   │           ├── UsernameNormalizer.java
│   │   │           ├── SecretRedactor.java
│   │   │           └── TimeProvider.java
│   │   │
│   │   └── resources/
│   │       ├── plugin.yml
│   │       ├── config.yml
│   │       ├── messages.yml
│   │       └── db/
│   │           └── migration/
│   │
│   └── test/
│       └── java/
│           └── com/example/authsecured/
│
└── docs/
    ├── architecture.md
    ├── security.md
    ├── database.md
    └── deployment.md
```

---

# 6. Paper / Purpur compatibility

Основная цель:

```text
Paper
  └── Paper API
       └── AuthSecured

Purpur
  └── Paper compatibility
       └── AuthSecured
```

Не использовать Purpur-specific API без необходимости.

Не использовать NMS для auth-механики.

Причина: Paper прямо рекомендует использовать API вместо Minecraft internals, поскольку NMS может меняться между версиями.

Если когда-либо понадобится NMS:

```text
paper/
  └── nms/
      ├── vX/
      └── NmsAdapter.java
```

и только через abstraction.

---

# 7. Java и build system

Использовать:

```text
Java 25+
Gradle Kotlin DSL
Paper API
HikariCP
Flyway
Argon2 library
JUnit 5
Testcontainers
```

Принцип:

```text
compileOnly Paper API
runtime dependencies -> shade into plugin JAR
```

Не требовать от пользователя вручную устанавливать библиотеку для Argon2/Hikari/Flyway.

Все runtime-зависимости должны быть корректно shaded/relocated.

---

# 8. Почему не использовать bcrypt как основной алгоритм

Для нового проекта основной вариант:

```text
Argon2id
```

Он предназначен для password hashing и memory-hard вычислений.

Рекомендуемая модель:

```text
password
   |
   v
Argon2id
   |
   +-- unique random salt
   +-- memory cost
   +-- time cost
   +-- parallelism
   |
   v
encoded password hash
```

Никогда:

```text
SHA-256(password)
MD5(password)
SHA-1(password)
```

и не использовать:

```text
SHA-256(password + salt)
```

как замену password hashing.

---

# 9. Password policy

Не делать слишком жёсткую policy вроде:

```text
минимум 20 символов
обязательно 3 спецсимвола
обязательно uppercase
```

Лучше:

```text
minimum length: 10
recommended: 12+
maximum length: 128
```

Пароль должен проверяться как Unicode-safe string.

Не логировать пароль ни при каких обстоятельствах.

Не хранить пароль в config, database, Redis или player metadata.

---

# 10. Argon2id configuration

Конфигурация должна быть параметризована:

```yaml
security:
  password:
    algorithm: ARGON2ID

    argon2:
      memory-kib: 65536
      iterations: 3
      parallelism: 2
      hash-length: 32
      salt-length: 16
```

Но реальные параметры необходимо benchmark-ить на конкретном сервере.

Цель:

```text
single password verification ≈ сотни миллисекунд
```

а не несколько секунд.

Важно:

**Argon2id нельзя выполнять на Minecraft main thread.**

Использовать отдельный bounded executor:

```text
Minecraft thread
      |
      v
LoginService
      |
      v
AuthExecutor
      |
      v
Argon2id
      |
      v
result
      |
      v
Minecraft scheduler
```

---

# 11. Защита от brute force

Нужны минимум два независимых rate limit:

```text
account-based
IP-based
```

Пример:

```yaml
security:
  rate-limit:
    login:
      per-account:
        max-attempts: 5
        window: 10m
        lockout: 15m

      per-ip:
        max-attempts: 20
        window: 10m
        lockout: 30m
```

Не полагаться только на IP.

Причина:

- несколько игроков могут находиться за одним NAT;
- IP может динамически измениться;
- attacker может использовать proxy rotation.

Также:

```text
global concurrent password verification limit
```

Например:

```text
maximum concurrent Argon2 jobs = CPU-dependent limit
```

Это защищает сервер от CPU/RAM exhaustion.

---

# 12. Progressive throttling

После неудачных попыток увеличивать задержку.

Например:

```text
1st failure  -> 0ms
2nd          -> 250ms
3rd          -> 500ms
4th          -> 1s
5th          -> 2s
6th+         -> temporary lock
```

Задержку применять на стороне сервера, а не через `Thread.sleep()` на main thread.

---

# 13. Registration protection

Регистрация должна иметь отдельные ограничения:

```text
max registrations / IP / time window
max accounts / IP
username validation
reserved names
```

Пример:

```yaml
security:
  registration:
    max-accounts-per-ip: 3
    max-attempts-per-hour: 10
```

---

# 14. Username handling

Никогда не строить безопасность только на имени игрока.

Хранить:

```text
player_uuid
username
normalized_username
```

`normalized_username` нужен для поиска и защиты от case mismatch.

Основной идентификатор аккаунта:

```text
UUID
```

Но в offline-mode нужно учитывать, что UUID зависит от имени игрока.

Для proxy-сети использовать корректную forwarding identity.

---

# 15. Online-mode и offline-mode

Плагин должен поддерживать два режима.

## online-mode=true

Mojang уже аутентифицирует игрока.

Рекомендуемое поведение:

```text
Minecraft authentication
        |
        v
trusted identity
        |
        v
optional local password auth disabled
```

Можно позволить режим:

```yaml
auth:
  online-mode:
    bypass-password: true
```

## offline-mode=false / cracked server

Тогда:

```text
join
  |
  v
AUTH_REQUIRED
  |
  v
/login password
```

Именно здесь password auth является критической защитой.

---

# 16. Proxy / Velocity

Для production сети рекомендуется:

```text
Internet
   |
   v
Velocity
   |
   +---- Lobby Paper
   |
   +---- Survival Paper
   |
   +---- Minigames Paper
```

Auth должен иметь единый security context.

Для нескольких серверов:

```text
Velocity
   |
   +---- Server A
   |
   +---- Server B
   |
   +---- Server C
          |
          v
       Redis
          |
          v
     PostgreSQL
```

Redis использовать для:

- distributed rate limits;
- временных authentication locks;
- cross-server session state;
- pub/sub для logout/revoke events.

PostgreSQL использовать как source of truth.

---

# 17. Session architecture

Не хранить plaintext session token.

После успешного `/login`:

```text
authenticated = true
session created
```

Сессия должна иметь:

```text
session_id
player_uuid
created_at
expires_at
server_id
```

Для single-server достаточно in-memory session.

Для multi-server:

```text
Redis
```

### Persistent sessions

По умолчанию:

```yaml
session:
  persistent: false
```

Это безопаснее.

После рестарта:

```text
all sessions invalidated
```

Игрок должен войти заново.

Если persistent session включается, токен должен быть:

```text
cryptographically random
single-use / rotatable
revocable
stored hashed
```

Не хранить сам токен в БД.

---

# 18. Session fixation protection

При каждом успешном login:

```text
old session -> revoke
new session -> create
```

При смене пароля:

```text
all sessions -> revoke
current session -> optionally re-authenticate
```

При logout:

```text
session -> revoke
```

---

# 19. Login flow

```text
PlayerJoinEvent
      |
      v
load account asynchronously
      |
      +---- not registered ----> AUTH_REQUIRED
      |
      +---- registered --------> AUTH_REQUIRED
      |
      v
apply restrictions
      |
      v
show login/register message
```

Игрок:

```text
/login password
```

Затем:

```text
validate input
      |
      v
rate limit
      |
      v
load account
      |
      v
Argon2id verify
      |
      +---- fail ---> increment counters
      |                 |
      |                 v
      |              possible lock
      |
      +---- success -> create session
                         |
                         v
                    AUTHENTICATED
                         |
                         v
                    remove restrictions
```

---

# 20. Password timing protection

Нельзя делать:

```text
if account == null:
    return "not registered"
```

сильно быстрее, чем реальный password verification.

Иначе можно делать username enumeration.

При неизвестном аккаунте желательно выполнять verification against a fixed dummy Argon2id hash.

```text
unknown username
      |
      v
dummy hash verification
      |
      v
generic authentication failure
```

Сообщение пользователю:

```text
Неверный логин или пароль.
```

а не:

```text
Игрок не зарегистрирован.
```

для `/login`.

Для `/register` можно явно сообщить, что имя уже зарегистрировано.

---

# 21. Player restrictions

Создать:

```text
PlayerRestrictionManager
```

Он должен знать:

```text
isAuthenticated(UUID)
```

и централизованно применять ограничения.

Не разбрасывать:

```java
if (!authenticated) ...
```

по десяткам классов.

Лучше:

```text
AuthStateService
        |
        v
RestrictionPolicy
        |
        v
Paper event listeners
```

---

# 22. Event protection

Минимальный набор событий:

```text
PlayerMoveEvent
PlayerTeleportEvent
PlayerCommandPreprocessEvent
AsyncPlayerChatEvent / соответствующий Paper chat API
InventoryClickEvent
InventoryDragEvent
InventoryOpenEvent
PlayerInteractEvent
PlayerInteractEntityEvent
BlockBreakEvent
BlockPlaceEvent
EntityDamageEvent
EntityDamageByEntityEvent
FoodLevelChangeEvent
PlayerDropItemEvent
PlayerPickupItemEvent
PlayerItemConsumeEvent
PlayerSwapHandItemsEvent
PlayerItemHeldEvent
VehicleEnterEvent
VehicleExitEvent
PlayerToggleSneakEvent
PlayerToggleSprintEvent
PlayerFishEvent
PlayerBedEnterEvent
PlayerShearEntityEvent
PlayerBucketEmptyEvent
PlayerBucketFillEvent
```

Список должен адаптироваться под актуальный Paper API.

---

# 23. Movement freeze

Лучше не телепортировать игрока каждый tick.

Хранить:

```text
preAuthLocation
```

и запрещать движение.

Если игрок пытается двигаться:

```text
cancel event
restore position only when necessary
```

Важно не создавать teleport loop.

---

# 24. Commands security

До авторизации:

```text
allowlist:
  - /login
  - /register
  - /changepassword
```

Не разрешать:

```text
/plugins
/version
/op
/permissions
/economy
/home
/tpa
/warp
```

и команды сторонних plugins.

### Важно

Auth plugin не должен пытаться контролировать абсолютно все сторонние команды через hardcoded список.

Лучше использовать policy:

```text
if AUTH_REQUIRED:
    command must belong to auth allowlist
```

---

# 25. Admin bypass

Администратор может иметь:

```text
authsecured.admin.bypass
```

Но bypass должен быть explicit.

Не использовать:

```text
*
```

как основу security model.

Также не давать bypass только потому, что игрок OP, если это можно избежать.

---

# 26. Database

Для production:

```text
PostgreSQL
```

Для маленького single-server:

```text
SQLite
```

Рекомендуемая abstraction:

```text
AccountRepository
```

и две реализации:

```text
PostgresAccountRepository
SqliteAccountRepository
```

---

# 27. PostgreSQL schema

## accounts

```sql
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,

    uuid UUID NOT NULL UNIQUE,

    username VARCHAR(16) NOT NULL,
    username_normalized VARCHAR(16) NOT NULL UNIQUE,

    password_hash TEXT NOT NULL,

    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',

    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ NULL,

    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_uuid
    ON accounts(uuid);

CREATE INDEX idx_accounts_username_normalized
    ON accounts(username_normalized);
```

---

# 28. Login attempts

Не хранить каждый пароль.

Хранить только metadata:

```sql
CREATE TABLE login_attempts (
    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NULL REFERENCES accounts(id) ON DELETE SET NULL,

    ip_hash BYTEA NOT NULL,

    success BOOLEAN NOT NULL,

    reason VARCHAR(64) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_login_attempts_created_at
    ON login_attempts(created_at);

CREATE INDEX idx_login_attempts_ip_hash
    ON login_attempts(ip_hash);

CREATE INDEX idx_login_attempts_account_id
    ON login_attempts(account_id);
```

---

# 29. Sessions

```sql
CREATE TABLE sessions (
    id UUID PRIMARY KEY,

    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,

    token_hash BYTEA NULL,

    server_id VARCHAR(64) NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,

    revoked_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_sessions_account_id
    ON sessions(account_id);

CREATE INDEX idx_sessions_expires_at
    ON sessions(expires_at);
```

---

# 30. Security audit

```sql
CREATE TABLE security_events (
    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NULL REFERENCES accounts(id) ON DELETE SET NULL,

    event_type VARCHAR(64) NOT NULL,

    ip_hash BYTEA NULL,

    metadata JSONB NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_security_events_created_at
    ON security_events(created_at);

CREATE INDEX idx_security_events_account_id
    ON security_events(account_id);

CREATE INDEX idx_security_events_type
    ON security_events(event_type);
```

Не сохранять:

```text
password
password hash
session token
raw authentication token
```

в `metadata`.

---

# 31. IP privacy

IP не обязательно хранить plaintext.

Рекомендуется:

```text
HMAC-SHA-256(server_secret, normalized_ip)
```

В БД:

```text
ip_hash
```

Плюсы:

- rate limiting;
- audit correlation;
- меньше PII;
- невозможно восстановить IP без secret.

Secret хранить не в database.

Например:

```yaml
security:
  ip-hashing:
    secret-env: AUTHSECURED_IP_SECRET
```

---

# 32. Redis architecture

Redis нужен только если есть несколько серверов или высокая нагрузка.

Использовать key namespaces:

```text
authsecured:ratelimit:ip:{hash}
authsecured:ratelimit:account:{id}
authsecured:lock:account:{id}
authsecured:session:{sessionId}
authsecured:player:{uuid}
authsecured:events
```

TTL должен устанавливаться всегда.

Никаких бесконечных ключей.

---

# 33. Redis не является source of truth

Правильно:

```text
PostgreSQL = durable truth
Redis = ephemeral state/cache
```

Неправильно:

```text
Redis only
```

Если Redis упал:

```text
database auth must remain available
```

Rate limit может перейти в локальный fallback.

---

# 34. Concurrency

Очень важно избежать race conditions.

Пример:

```text
Player A
  register
      |
      +--------+
               |
Player A       |
  register ----+
```

Оба запроса не должны создать две записи.

Использовать:

```text
UNIQUE(username_normalized)
UNIQUE(uuid)
```

и обрабатывать constraint violation.

---

# 35. Account locking

Не блокировать аккаунт навсегда после нескольких неправильных паролей.

Рекомендуемая схема:

```text
5 failures
   |
   v
15 min lock
```

Повторные abuse attempts могут увеличить lock:

```text
15m
30m
1h
2h
```

Но нужен maximum:

```text
max lock = configurable
```

Администратор может unlock:

```text
/authadmin unlock <player>
```

---

# 36. Anti-bot layer

Без 2FA можно использовать несколько уровней:

### Layer 1

Rate limit.

### Layer 2

Concurrent Argon2 limit.

### Layer 3

Registration limits.

### Layer 4

Progressive delays.

### Layer 5

Temporary account/IP locks.

### Layer 6

Optional challenge после подозрительной активности.

Например:

```text
/login password
```

после большого количества подозрительных запросов может потребовать простой server-side challenge.

Это **не 2FA**.

Но challenge должен быть отключаемым:

```yaml
security:
  challenge:
    enabled: false
```

---

# 37. Password change

Команда:

```text
/changepassword <old> <new>
```

Flow:

```text
authenticated
      |
      v
verify old password
      |
      v
validate new password
      |
      v
Argon2id(new password)
      |
      v
update account
      |
      v
revoke all sessions
```

После смены пароля:

```text
all existing sessions invalidated
```

---

# 38. Password reset

Игрок не должен иметь:

```text
/forgotpassword
```

который отправляет пароль или reset token через обычный Minecraft chat.

Вместо этого:

```text
/admin resetpassword
```

или отдельная безопасная admin workflow.

Никогда не:

```text
send current password
```

потому что текущий пароль вообще невозможно восстановить из Argon2id hash.

---

# 39. Logging

Безопасный лог:

```text
LOGIN_SUCCESS uuid=...
LOGIN_FAILURE uuid=... reason=INVALID_PASSWORD
ACCOUNT_LOCKED uuid=...
SESSION_REVOKED uuid=...
```

Никогда:

```text
/login password123
password=password123
hash=...
sessionToken=...
```

Также нужно сделать:

```text
SecretRedactor
```

для исключения случайной утечки secrets.

---

# 40. Configuration

Пример:

```yaml
database:
  type: postgresql

  postgresql:
    host: localhost
    port: 5432
    database: authsecured
    username: authsecured
    password-env: AUTHSECURED_DB_PASSWORD
    ssl: true
    pool-size: 10

  sqlite:
    file: auth.db

security:
  password:
    algorithm: ARGON2ID

    min-length: 10
    max-length: 128

    argon2:
      memory-kib: 65536
      iterations: 3
      parallelism: 2
      hash-length: 32
      salt-length: 16

  login:
    timeout-seconds: 60

  rate-limit:
    login:
      per-account:
        max-attempts: 5
        window-seconds: 600
        lock-seconds: 900

      per-ip:
        max-attempts: 20
        window-seconds: 600
        lock-seconds: 1800

  registration:
    max-accounts-per-ip: 3
    max-attempts-per-hour: 10

  challenge:
    enabled: false

session:
  persistent: false
  timeout-minutes: 30

redis:
  enabled: false
  uri-env: AUTHSECURED_REDIS_URI

proxy:
  velocity:
    enabled: false

auth:
  online-mode:
    bypass-password: true

restrictions:
  freeze-player: true
  block-chat: true
  block-commands: true
  block-inventory: true
  block-interactions: true

logging:
  audit:
    enabled: true
  debug: false
```

---

# 41. Secrets

Не хранить production credentials:

```yaml
password: myPassword123
```

в `config.yml`.

Использовать environment variables:

```text
AUTHSECURED_DB_PASSWORD
AUTHSECURED_REDIS_URI
AUTHSECURED_IP_SECRET
```

или secret manager.

---

# 42. Database connection

Использовать HikariCP.

Никогда не выполнять:

```text
JDBC query
```

на main thread.

Правильный flow:

```text
Minecraft thread
      |
      v
CompletableFuture
      |
      v
DB executor / HikariCP
      |
      v
PostgreSQL
      |
      v
CompletableFuture
      |
      v
Minecraft scheduler
```

---

# 43. Async rules

### Main thread

Разрешено:

```text
Player API
World API
Inventory API
Bukkit/Paper UI
```

### Async

Разрешено:

```text
PostgreSQL
Redis
Argon2id
file I/O
audit persistence
```

Нельзя случайно вызвать:

```java
player.teleport(...)
player.sendMessage(...)
player.getInventory()
```

из arbitrary async thread.

---

# 44. Service boundaries

### AuthService

Оркестратор:

```text
register()
login()
logout()
changePassword()
```

### PasswordService

```text
hash()
verify()
validatePolicy()
```

### SessionService

```text
create()
revoke()
revokeAll()
isValid()
```

### RateLimitService

```text
checkLogin()
recordFailure()
recordSuccess()
checkRegistration()
```

### SecurityService

```text
recordEvent()
lockAccount()
unlockAccount()
```

---

# 45. Auth result

Не возвращать просто:

```text
boolean
```

Лучше:

```text
AuthResult
```

Например:

```text
SUCCESS
ACCOUNT_NOT_FOUND
INVALID_PASSWORD
RATE_LIMITED
ACCOUNT_LOCKED
ALREADY_AUTHENTICATED
NOT_REGISTERED
VALIDATION_FAILED
INTERNAL_ERROR
```

Presentation layer решает, какое сообщение показать игроку.

---

# 46. Error handling

Игроку:

```text
Произошла ошибка авторизации. Попробуйте позже.
```

В лог:

```text
DatabaseUnavailable
ConnectionTimeout
Argon2ExecutorRejected
```

Не показывать:

```text
SQL exception
database hostname
stack trace
internal class names
```

---

# 47. Startup sequence

```text
onLoad
   |
   v
read config
   |
   v
validate config
   |
   v
initialize dependency container
```

После enable:

```text
initialize database
   |
   v
run migrations
   |
   v
initialize repositories
   |
   v
initialize auth services
   |
   v
register listeners
   |
   v
register commands
   |
   v
start maintenance tasks
```

Если database migration не прошла:

```text
FAIL FAST
```

Не запускать auth plugin в half-working state.

---

# 48. Shutdown

При disable:

```text
stop accepting auth jobs
      |
      v
wait bounded time for async jobs
      |
      v
flush audit events
      |
      v
close Redis
      |
      v
close Hikari
      |
      v
shutdown executors
```

Никогда не зависать на shutdown бесконечно.

---

# 49. Cleanup jobs

Периодически удалять старые:

```text
login_attempts
security_events
sessions
```

Например:

```yaml
retention:
  login-attempts-days: 30
  security-events-days: 90
  sessions-days: 7
```

Удаление выполнять batch-операциями async.

---

# 50. Metrics

Опционально добавить metrics:

```text
authsecured_login_success_total
authsecured_login_failure_total
authsecured_registration_total
authsecured_locked_accounts
authsecured_active_sessions
authsecured_argon2_duration
authsecured_db_query_duration
authsecured_rate_limited_total
```

Не отправлять password/username/IP в telemetry.

---

# 51. Testing

Обязательные тесты.

## Unit

```text
UsernameNormalizerTest
PasswordPolicyTest
Argon2PasswordHasherTest
RateLimitServiceTest
SessionServiceTest
AuthStateTest
```

## Integration

```text
PostgreSQL repository
SQLite repository
Redis rate limit
Flyway migrations
```

Использовать:

```text
Testcontainers
```

для PostgreSQL/Redis.

---

# 52. Security tests

Проверить:

```text
brute force
account enumeration
timing behavior
race registration
race login
session fixation
session revoke
password change
IP rate limit
account rate limit
concurrent Argon2 jobs
DB unavailable
Redis unavailable
server restart
proxy reconnect
```

---

# 53. Fuzzing / abuse tests

Особенно проверять:

```text
empty username
unicode username
very long password
128-char password
129-char password
null-like input
malformed commands
rapid reconnect
rapid register
rapid login
thousands of failed login attempts
```

---

# 54. Security checklist перед релизом

```text
[ ] Passwords never logged
[ ] Password hashes never logged
[ ] Argon2id used
[ ] Unique salt per password
[ ] Argon2 never runs on main thread
[ ] Login rate limiting
[ ] Registration rate limiting
[ ] IP rate limiting
[ ] Account lock
[ ] Concurrent hash limit
[ ] Session revocation
[ ] Password change revokes sessions
[ ] No persistent sessions by default
[ ] SQL parameters used everywhere
[ ] DB credentials outside config
[ ] IPs hashed/HMACed where possible
[ ] Audit logs don't contain secrets
[ ] Auth-required player is frozen
[ ] Auth-required player can't execute arbitrary commands
[ ] Auth-required player can't interact with world
[ ] Auth-required player can't bypass via plugin messages
[ ] Database operations are async
[ ] Redis is optional
[ ] Redis isn't source of truth
[ ] PostgreSQL constraints protect race conditions
[ ] Migrations tested
[ ] Graceful shutdown
[ ] No NMS dependency in auth core
[ ] Paper/Purpur tested
[ ] Proxy deployment tested
```

---

# 55. Recommended dependency stack

```text
Minecraft server:
    Paper / Purpur

Language:
    Java 25+

Build:
    Gradle Kotlin DSL

API:
    Paper API

Database:
    PostgreSQL
    SQLite optional

Connection pool:
    HikariCP

Migrations:
    Flyway

Password hashing:
    Argon2id

Distributed state:
    Redis optional

Testing:
    JUnit 5
    Testcontainers

Architecture:
    Clean Architecture / Hexagonal Architecture
```

---

# 56. Why this architecture

Главная идея — не делать огромный `Main.java`, где одновременно находятся:

```text
commands
SQL
hashing
events
sessions
configuration
security
```

Вместо этого:

```text
Paper API
   |
   v
Adapters
   |
   v
Application services
   |
   v
Domain
   |
   v
Ports
   |
   +---- PostgreSQL
   +---- SQLite
   +---- Redis
   +---- Argon2id
```

Это позволяет:

- легко обновлять Minecraft;
- поддерживать Paper/Purpur;
- тестировать security без запуска Minecraft;
- менять PostgreSQL на SQLite;
- подключать Redis только при необходимости;
- добавлять Velocity;
- менять Argon2 parameters;
- не зависеть от NMS.

---

# 57. Что НЕ нужно делать

Не использовать:

```text
MD5
SHA-1
SHA-256(password)
plain-text passwords
password encryption instead of hashing
passwords in YAML
passwords in Redis
passwords in logs
NMS for ordinary auth logic
SQL on main thread
Argon2 on main thread
Thread.sleep() on main thread
IP-only account identity
IP-only brute-force protection
permanent bans after a few wrong passwords
persistent sessions by default
Redis-only account storage
```

---

# 58. MVP

Первую рабочую версию делать в таком порядке:

### Phase 1

```text
Gradle
Paper API
plugin.yml
config
database
Flyway
AccountRepository
Argon2id
```

### Phase 2

```text
/register
/login
/logout
/changepassword
```

### Phase 3

```text
Player restrictions
Command allowlist
Session manager
```

### Phase 4

```text
Rate limiting
Account lock
IP HMAC
Audit log
```

### Phase 5

```text
PostgreSQL
SQLite
Redis
```

### Phase 6

```text
Velocity
multi-server sessions
distributed rate limits
```

### Phase 7

```text
Tests
security tests
load tests
documentation
```

---

# 59. Production topology

Для одного сервера:

```text
Paper/Purpur
   |
   +---- AuthSecured
   |
   +---- PostgreSQL
```

Для небольшого production:

```text
Internet
   |
   v
Velocity
   |
   v
Paper/Purpur
   |
   +---- AuthSecured
   |
   +---- PostgreSQL
   |
   +---- Redis
```

Для нескольких backend:

```text
                       ┌── Paper #1
                       │
Internet -> Velocity ──┼── Paper #2
                       │
                       └── Paper #3
                              |
                       AuthSecured
                              |
                    ┌─────────┴─────────┐
                    v                   v
               PostgreSQL             Redis
               source truth        distributed state
```

---

# 60. Final design decision

Итоговый production stack:

```text
Paper/Purpur
      +
Java 25+
      +
Gradle Kotlin DSL
      +
Paper API
      +
Clean/Hexagonal Architecture
      +
Argon2id
      +
HikariCP
      +
PostgreSQL
      +
Redis optional
      +
Flyway
      +
JUnit 5
      +
Testcontainers
```

### Security priority

```text
1. Argon2id
2. No plaintext secrets
3. Async hashing
4. Account + IP rate limiting
5. Concurrent hash limit
6. Temporary account locks
7. Pre-auth player isolation
8. Session revocation
9. PostgreSQL constraints
10. Proxy/backend network isolation
11. Security audit
12. Automated security tests
```

### Главное правило

**Не пытаться решить безопасность только авторизацией.**

Самая сильная схема:

```text
AuthSecured
   +
Paper/Purpur hardening
   +
Velocity
   +
private backend network
   +
PostgreSQL
   +
Redis rate limiting
   +
Argon2id
   +
strict pre-auth isolation
```

Именно такая комбинация даёт существенно более сильную защиту, чем просто `/register` + `/login`.

---

## 61. Следующий этап разработки

После утверждения этой архитектуры проект следует реализовывать по отдельным этапам:

```text
1. build.gradle.kts + project skeleton
2. plugin.yml + config.yml
3. domain models
4. PostgreSQL schema + Flyway migrations
5. HikariCP DatabaseManager
6. Argon2id PasswordHasher
7. AccountRepository
8. AuthService
9. SessionService
10. PlayerRestrictionManager
11. /register + /login
12. rate limits
13. security audit
14. Redis adapter
15. Velocity integration
16. integration tests
17. security/load tests
18. production documentation
```

Каждый следующий этап должен сохранять границы слоёв и не переносить database/security logic в Paper event listeners.
