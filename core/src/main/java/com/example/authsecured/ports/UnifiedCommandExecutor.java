package com.example.authsecured.ports;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Unified entrypoint for authentication commands across all platforms.
 */
public interface UnifiedCommandExecutor {
    CompletableFuture<Void> executeRegister(UUID playerUuid, String playerName, char[] password, char[] confirmPassword, String ipAddress);
    CompletableFuture<Void> executeLogin(UUID playerUuid, String playerName, char[] password, String ipAddress);
    CompletableFuture<Void> executeChangePassword(UUID playerUuid, char[] oldPassword, char[] newPassword, String ipAddress);
    CompletableFuture<Void> executeLogout(UUID playerUuid);
    void executeAuthStatus(UUID playerUuid);
}
