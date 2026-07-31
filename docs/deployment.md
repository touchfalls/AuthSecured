# Deployment Guide

## Production Network Topology

```text
Internet ──► Velocity Proxy ──(Private Network)──► Paper Backend (AuthSecured) ──► PostgreSQL / Redis
```

## Recommended Environment Variables
- `AUTHSECURED_DB_PASSWORD`: PostgreSQL database password
- `AUTHSECURED_IP_SECRET`: Server secret key for IP HMAC hashing
- `AUTHSECURED_REDIS_URI`: Optional Redis connection string
