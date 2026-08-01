package com.example.authsecured.ports;

import java.io.File;
import java.util.UUID;

/**
 * Port representing the hosting Minecraft server platform (Paper / Purpur / Bukkit).
 */
public interface AuthPlatform {
    File getDataFolder();
    PlatformLogger getLogger();
    PlayerRestrictionAdapter getRestrictionAdapter();
    void sendMessage(UUID playerUuid, String message);
    void runTaskOnMainThread(Runnable task);
    boolean isOnlineMode();
}
