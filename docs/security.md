# Security Architecture

## Password Hashing: Argon2id
AuthSecured uses Argon2id with:
- 64MB memory limit (`65536 KiB`)
- 3 iterations
- 2 parallelism threads
- 32-byte hash length
- 16-byte salt length

## Timing Attack Mitigation
When a user attempts to log into an unregistered account name, a dummy hash calculation is performed asynchronously before returning a generic invalid credentials message.

## Pre-Auth Player Restrictions
Players awaiting authentication are:
- Frozen at join coordinates
- Blocked from inventory, chat, external commands, entity damage, interactions, block placement, and item drops.
