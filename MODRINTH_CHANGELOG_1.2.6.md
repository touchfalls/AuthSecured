# AuthSecured 1.2.6

Includes the 1.2.5 Fabric compatibility fix plus three community contributions from @nicki41.

## What's new

- `/authadmin doctor` now also reports IP-unbound sessions on an offline-mode server, Argon2
  settings below the OWASP minimum, and unencrypted PostgreSQL connections to a remote host.
- Paper: players who have not logged in only see (and can tab-complete) the commands in
  `restrictions.allowed-commands`, so a player joining under an operator's name can no longer
  browse that operator's commands before logging in.
- The Argon2 and database worker threads have descriptive names, which makes thread dumps and
  profiler output (spark) readable.

## Contributors

A big thank you to [@nicki41](https://github.com/nicki41) for three pull requests in this
release: the new doctor checks (#8), hiding commands before login on Paper (#9) and the
descriptive worker thread names (#10).

## Fabric compatibility

- The bundled SQLite driver is 3.49.1.0 (since 1.2.5). It cannot be relocated and takes
  precedence over other mods' copies; the older 3.53.x made Ledger crash on startup with an
  `ALTER TABLE players MODIFY COLUMN` error.

## Notes

- No configuration changes are required.
- Paper and Fabric remain separate downloads; each Fabric file targets one exact Minecraft
  version and requires Fabric API.
