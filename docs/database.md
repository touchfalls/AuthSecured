# Database & Migrations

AuthSecured supports PostgreSQL and SQLite.

## Schema
- `accounts`: User credentials, normalized username, UUID, and lockout status.
- `login_attempts`: Immutable login attempt logs with hashed IP.
- `sessions`: Session tracking table with token hash and expiration.
- `security_events`: High-level security audit trail.

Migrations are applied automatically via Flyway for PostgreSQL and DB script runner for SQLite.
