package com.example.authsecured.paper.adapter;

import com.example.authsecured.ports.AuthPlatform;
import com.example.authsecured.ports.PlatformLogger;
import com.example.authsecured.ports.PlayerRestrictionAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.UUID;

public class PaperAuthPlatform implements AuthPlatform {

    private final Plugin plugin;
    private final PlatformLogger logger;
    private final PlayerRestrictionAdapter restrictionAdapter;

    public PaperAuthPlatform(Plugin plugin, PlayerRestrictionAdapter restrictionAdapter) {
        this.plugin = plugin;
        this.logger = new PaperLogger(plugin.getLogger());
        this.restrictionAdapter = restrictionAdapter;
    }

    @Override
    public File getDataFolder() {
        return plugin.getDataFolder();
    }

    @Override
    public PlatformLogger getLogger() {
        return logger;
    }

    @Override
    public PlayerRestrictionAdapter getRestrictionAdapter() {
        return restrictionAdapter;
    }

    @Override
    public void sendMessage(UUID playerUuid, String message) {
        if (message == null || message.isBlank()) return;
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

    @Override
    public void runTaskOnMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public boolean isOnlineMode() {
        return Bukkit.getOnlineMode();
    }
}
