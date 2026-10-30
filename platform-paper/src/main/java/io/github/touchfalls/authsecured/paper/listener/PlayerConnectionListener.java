package io.github.touchfalls.authsecured.paper.listener;

import io.github.touchfalls.authsecured.application.AuthService;
import io.github.touchfalls.authsecured.application.AuthenticationTimeoutService;
import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import io.github.touchfalls.authsecured.infrastructure.security.IpHashService;
import io.github.touchfalls.authsecured.paper.player.PlayerRestrictionManager;
import io.github.touchfalls.authsecured.paper.scheduler.PaperScheduler;
import io.github.touchfalls.authsecured.util.PremiumIdentity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;

public class PlayerConnectionListener implements Listener {

    private final AuthService authService;
    private final PlayerRestrictionManager restrictionManager;
    private final ConfigManager configManager;
    private final IpHashService ipHashService;
    private final AuthenticationTimeoutService authenticationTimeoutService;
    private final Plugin plugin;
    private final PaperScheduler scheduler;

    public PlayerConnectionListener(AuthService authService, PlayerRestrictionManager restrictionManager,
                                    ConfigManager configManager, IpHashService ipHashService,
                                    AuthenticationTimeoutService authenticationTimeoutService, Plugin plugin,
                                    PaperScheduler scheduler) {
        this.authService = authService;
        this.restrictionManager = restrictionManager;
        this.configManager = configManager;
        this.ipHashService = ipHashService;
        this.authenticationTimeoutService = authenticationTimeoutService;
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    /**
     * Vanilla kicks the online player when the same UUID joins again. In offline mode anyone can join
     * under another player's name, so an authenticated player is only replaced by a connection from
     * the same address (a real reconnect after a dropped connection). The name is compared without
     * case, because "sTeve" is a different UUID but looks like "Steve".
     */
    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        for (Player online : Bukkit.getOnlinePlayers()) {
            boolean sameIdentity = online.getUniqueId().equals(event.getUniqueId())
                    || online.getName().equalsIgnoreCase(event.getName());
            InetSocketAddress onlineSocket = online.getAddress();
            if (sameIdentity && isForeignReplacement(restrictionManager.isAuthenticated(online.getUniqueId()),
                    onlineSocket == null ? null : onlineSocket.getAddress(), event.getAddress())) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, configManager.getMessage(
                        "messages.duplicate-login-denied", "&cThis player is already online."));
                return;
            }
        }
    }

    static boolean isForeignReplacement(boolean onlineAuthenticated, InetAddress onlineAddress, InetAddress newAddress) {
        return onlineAuthenticated && onlineAddress != null && !onlineAddress.equals(newAddress);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        restrictionManager.setPlayerUnauthenticated(uuid, player.getLocation());
        authenticationTimeoutService.start(uuid);

        boolean onlineModeBypass = configManager.getBoolean("auth.online-mode.bypass-password", true);
        if (onlineModeBypass && PremiumIdentity.isVerified(uuid, Bukkit.getOnlineMode(),
                configManager.getBoolean("auth.online-mode.trust-proxy-uuid", false))) {
            restrictionManager.setPlayerAuthenticated(uuid);
            authenticationTimeoutService.clear(uuid);
            player.sendMessage(configManager.getMessage("messages.auth-bypass", "&aOnline-mode active: Automatically authenticated!"));
            return;
        }

        InetSocketAddress socketAddress = player.getAddress();
        String rawIp = (socketAddress != null && socketAddress.getAddress() != null) ? socketAddress.getAddress().getHostAddress() : "";
        byte[] hashedIp = ipHashService.hashIp(rawIp);

        authService.getSessionService().isValidActiveSession(uuid, hashedIp).thenAccept(hasActiveSession -> {
            scheduler.schedule(player, () -> {
                if (!isCurrentPlayer(player, uuid)) return;
                if (hasActiveSession) {
                    restrictionManager.setPlayerAuthenticated(uuid);
                    authenticationTimeoutService.clear(uuid);
                    player.sendMessage(configManager.getMessage("messages.session-restored", "&aSession restored automatically!"));
                } else {
                    authService.isRegistered(uuid).thenAccept(registered -> {
                        scheduler.schedule(player, () -> {
                            if (!isCurrentPlayer(player, uuid)) return;
                            if (registered) {
                                player.sendMessage(configManager.getMessage("messages.login-prompt", "&ePlease log in using /login <password>"));
                            } else {
                                player.sendMessage(configManager.getMessage("messages.register-prompt", "&ePlease register using /register <password> <password>"));
                            }
                        });
                    }).exceptionally(ex -> {
                        plugin.getLogger().severe("Registration status check failed for " + playerName + ".");
                        return null;
                    });
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Session validation failed for " + playerName + ".");
            return null;
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        boolean authenticated = restrictionManager.isAuthenticated(uuid);
        authenticationTimeoutService.clear(uuid);
        restrictionManager.removePlayer(uuid);
        if (authenticated) {
            // With session.extend-while-online the session lifetime now starts at disconnect.
            authService.getSessionService().extendActiveSessions(List.of(uuid)).exceptionally(ex -> {
                plugin.getLogger().severe("Session extension failed for " + event.getPlayer().getName() + ".");
                return null;
            });
        }
    }

    private static boolean isCurrentPlayer(Player player, UUID uuid) {
        return player.isOnline() && Bukkit.getPlayer(uuid) == player;
    }
}
