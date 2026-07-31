# Security Policy

## Threat Model & Guarantees

AuthSecured is built against common security risks:
1. **Password Storage**: Argon2id with 64MB memory cost, 3 iterations, and 2 parallelism threads.
2. **Timing Attacks**: Fixed dummy Argon2id hash verification is executed for non-existent users on login attempts.
3. **Main Thread Safety**: Hashing runs strictly on bounded background thread pools.
4. **Data Leakage**: Passwords, hashes, and tokens are never written to server console logs or audit tables.
5. **IP Privacy**: All stored IP records use HMAC-SHA256 with a configurable secret environment variable (`AUTHSECURED_IP_SECRET`).

## Reporting Security Vulnerabilities

Please do NOT report security vulnerabilities in public issues. Submit reports privately to the project maintainers.
