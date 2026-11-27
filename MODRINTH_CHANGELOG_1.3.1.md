# AuthSecured 1.3.1

This update improves pre-login protections, password handling, and session recovery on Paper and Fabric.

## Security and audit

- Failed authenticator checks run one at a time per player, and saved sessions respect account
  suspensions and locks.
- The audit log records account locks, saved-session revocations, and administrative account
  deletions. Revoking saved sessions keeps the current player connected.
- Paper now filters password commands with arguments from console logs, including lines written
  by other plugins. Chat text with the same pattern is filtered as well.
- When an administrator resets a password in-game, AuthSecured reminds them to clear chat with
  `F3 + D` after handling the temporary password.
- Fabric blocks more interactions before login and refuses an offline-mode connection from another
  address when that name already belongs to a logged-in player.

## Fixes

- Paper restores normal duplicate-login behavior on online-mode servers.
- Saved sessions work across multiple addresses. Shared rate limits recover automatically after
  Redis reconnects, and one address can use at most half of the password-hashing queue.
- Diagnostics now clarify shared database secret requirements and warn when a remote Redis
  connection is unencrypted.

## Downloads

Use the Paper JAR for Paper-compatible servers. On Fabric, choose the JAR for your exact Minecraft
version and install the matching Fabric API. Supported versions range from Minecraft 1.21 to 26.3.
