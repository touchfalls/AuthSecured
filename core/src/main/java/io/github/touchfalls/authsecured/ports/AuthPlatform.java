package io.github.touchfalls.authsecured.ports;

import java.io.File;
import java.util.UUID;

/**
 * Port representing the hosting Minecraft server platform.
 */
public interface AuthPlatform {
    File getDataFolder();
    PlatformLogger getLogger();
    PlayerRestrictionAdapter getRestrictionAdapter();
    void sendMessage(UUID playerUuid, String message);
    void runTaskOnMainThread(Runnable task);

    /**
     * Runs a task that reads or changes a player's state on the thread that owns that player.
     * That is the server thread everywhere except Folia, where each region has its own thread.
     */
    default void runTaskForPlayer(UUID playerUuid, Runnable task) {
        runTaskOnMainThread(task);
    }
    boolean isOnlineMode();

    /** Remote IP addresses (as text) of the players that are online now; empty when unknown. */
    default java.util.Collection<String> onlinePlayerAddresses() {
        return java.util.List.of();
    }

    boolean isPlayerOnline(UUID playerUuid);

    /**
     * Opaque identity of the player's current connection, distinct even when a
     * player with the same UUID disconnects and reconnects while an earlier
     * asynchronous result (for example Argon2 password hashing) is still
     * pending. Null if the player is not currently connected.
     */
    Object currentConnectionToken(UUID playerUuid);
    void disconnectPlayer(UUID playerUuid, String reason);
}
