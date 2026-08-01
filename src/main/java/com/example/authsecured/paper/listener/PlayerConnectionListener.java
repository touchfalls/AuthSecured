package com.example.authsecured.paper.listener;

import com.example.authsecured.application.AuthService;
import com.example.authsecured.infrastructure.config.ConfigManager;
import com.example.authsecured.infrastructure.security.IpHashService;
import com.example.authsecured.paper.player.PlayerRestrictionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.net.InetSocketAddress;
import java.util.UUID;

public class PlayerConnectionListener implements Listener {

    private final AuthService authService;
    private final PlayerRestrictionManager restrictionManager;
    private final ConfigManager configManager;
    private final IpHashService ipHashService;
    private final Plugin plugin;

    public PlayerConnectionListener(AuthService authService, PlayerRestrictionManager restrictionManager,
                                    ConfigManager configManager, IpHashService ipHashService, Plugin plugin) {
        this.authService = authService;
        this.restrictionManager = restrictionManager;
        this.configManager = configManager;
        this.ipHashService = ipHashService;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        restrictionManager.setPlayerUnauthenticated(uuid, player.getLocation());

        boolean onlineModeBypass = configManager.getBoolean("auth.online-mode.bypass-password", true);
        if (onlineModeBypass && Bukkit.getOnlineMode()) {
            restrictionManager.setPlayerAuthenticated(uuid);
            player.sendMessage(configManager.getMessage("messages.auth-bypass", "&aOnline-mode active: Automatically authenticated!"));
            return;
        }

        InetSocketAddress socketAddress = player.getAddress();
        String rawIp = (socketAddress != null && socketAddress.getAddress() != null) ? socketAddress.getAddress().getHostAddress() : "";
        String hashedIp = ipHashService.hashIpToString(rawIp);

        authService.getSessionService().isValidActiveSession(uuid, hashedIp).thenAccept(hasActiveSession -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (hasActiveSession) {
                    restrictionManager.setPlayerAuthenticated(uuid);
                    player.sendMessage(configManager.getMessage("messages.session-restored", "&aSession restored automatically!"));
                } else {
                    authService.isRegistered(uuid).thenAccept(registered -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) return;
                            if (registered) {
                                player.sendMessage(configManager.getMessage("messages.login-prompt", "&ePlease log in using /login <password>"));
                            } else {
                                player.sendMessage(configManager.getMessage("messages.register-prompt", "&ePlease register using /register <password> <password>"));
                            }
                        });
                    }).exceptionally(ex -> {
                        plugin.getLogger().severe("Error checking registration status for " + player.getName() + ": " + ex.getMessage());
                        return null;
                    });
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error validating session for " + player.getName() + ": " + ex.getMessage());
            return null;
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        restrictionManager.removePlayer(uuid);
    }
}
