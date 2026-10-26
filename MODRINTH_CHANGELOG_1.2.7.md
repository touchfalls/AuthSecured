# AuthSecured 1.2.7

Security follow-ups from the 1.2.6 review, plus Folia support and a new session option from
@nicki41.

## What's new

- Paper now runs on Folia.
- New option `session.extend-while-online` (default `false`): the session timeout counts from
  when a player leaves instead of from login. Useful on proxy networks sharing one database.

## Security and fixes

- Simultaneous registrations can no longer exceed `max-accounts-per-ip`; the limit is enforced
  atomically, also across servers sharing a PostgreSQL database.
- `/authadmin doctor` no longer treats hosts like `127.db.example.com` as loopback.
- Audit and login records queued at shutdown are no longer lost.

## Notes

- No configuration changes are required.
- Folia support has been covered only by the unit-test suite, not tested on a live Folia server.
- Paper and Fabric remain separate downloads; each Fabric file targets one exact Minecraft
  version and requires Fabric API.
