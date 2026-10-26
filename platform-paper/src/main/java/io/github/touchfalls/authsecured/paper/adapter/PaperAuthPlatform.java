package io.github.touchfalls.authsecured.paper.adapter;

import io.github.touchfalls.authsecured.paper.scheduler.PaperScheduler;
import io.github.touchfalls.authsecured.ports.AuthPlatform;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import io.github.touchfalls.authsecured.ports.PlayerRestrictionAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.UUID;

public class PaperAuthPlatform implements AuthPlatform {

    private final Plugin plugin;
    private final PlatformLogger logger;
    private final PlayerRestrictionAdapter restrictionAdapter;
    private final PaperScheduler scheduler;

    public PaperAuthPlatform(Plugin plugin, PlayerRestrictionAdapter restrictionAdapter, PaperScheduler scheduler) {
        this.plugin = plugin;
        this.logger = new PaperLogger(plugin.getLogger());
        this.restrictionAdapter = restrictionAdapter;
        this.scheduler = scheduler;
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
        scheduler.runOrScheduleGlobal(task);
    }

    @Override
    public void runTaskForPlayer(UUID playerUuid, Runnable task) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            // The task finds the player gone and only cleans up; no region owns them anymore.
            scheduler.runOrScheduleGlobal(task);
        } else {
            scheduler.runOrSchedule(player, task);
        }
    }

    @Override
    public boolean isOnlineMode() {
        return Bukkit.getOnlineMode();
    }

    @Override
    public boolean isPlayerOnline(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        return player != null && player.isOnline();
    }

    @Override
    public Object currentConnectionToken(UUID playerUuid) {
        // A reconnecting player gets a new Player instance; its identity is a safe token.
        return Bukkit.getPlayer(playerUuid);
    }

    @Override
    public void disconnectPlayer(UUID playerUuid, String reason) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            scheduler.runOrSchedule(player, () -> player.kickPlayer(reason));
        }
    }
}
