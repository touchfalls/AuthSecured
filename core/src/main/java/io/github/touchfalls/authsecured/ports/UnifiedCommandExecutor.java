package io.github.touchfalls.authsecured.ports;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Unified entrypoint for authentication commands across all platforms.
 */
public interface UnifiedCommandExecutor {
    CompletableFuture<Void> executeRegister(UUID playerUuid, String playerName, char[] password, char[] confirmPassword, String ipAddress);
    CompletableFuture<Void> executeLogin(UUID playerUuid, String playerName, char[] password, String ipAddress);
    /** The player name is used for the account rate limit, the same key {@code /login} uses. */
    CompletableFuture<Void> executeChangePassword(UUID playerUuid, String playerName, char[] oldPassword,
                                                  char[] newPassword, String ipAddress);
    CompletableFuture<Void> executeLogout(UUID playerUuid);
    void executeAuthStatus(UUID playerUuid);

    /**
     * {@code /2fa}: {@code args} is everything after the command name. {@code enable}, {@code confirm <code>},
     * {@code disable <code>} and {@code status} manage the authenticator of a logged-in player; a bare code
     * finishes a login that already passed the password step.
     */
    CompletableFuture<Void> executeTwoFactor(UUID playerUuid, String playerName, String[] args, String ipAddress);

    /**
     * {@code /sessions}: tells an authenticated player how many saved sessions their account has;
     * with {@code revoke} it ends all of them but keeps this connection logged in.
     */
    CompletableFuture<Void> executeSessions(UUID playerUuid, boolean revoke);

    /**
     * Returns an online player to the pre-login state after an administrator invalidated
     * their credentials, so a connection that authenticated earlier cannot outlive them.
     * Does nothing if the player is offline.
     */
    void revokeAuthentication(UUID playerUuid, String messageKey, String fallbackMessage);
}
