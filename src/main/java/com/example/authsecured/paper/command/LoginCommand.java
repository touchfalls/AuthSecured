package com.example.authsecured.paper.command;

import com.example.authsecured.application.AuthService;
import com.example.authsecured.infrastructure.config.ConfigManager;
import com.example.authsecured.infrastructure.security.IpHashService;
import com.example.authsecured.paper.player.PlayerRestrictionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public class LoginCommand implements CommandExecutor {

    private final AuthService authService;
    private final PlayerRestrictionManager restrictionManager;
    private final ConfigManager configManager;
    private final IpHashService ipHashService;
    private final Plugin plugin;

    public LoginCommand(AuthService authService, PlayerRestrictionManager restrictionManager,
                        ConfigManager configManager, IpHashService ipHashService, Plugin plugin) {
        this.authService = authService;
        this.restrictionManager = restrictionManager;
        this.configManager = configManager;
        this.ipHashService = ipHashService;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (restrictionManager.isAuthenticated(uuid)) {
            player.sendMessage(configManager.getMessage("messages.already-authenticated", "&cYou are already logged in!"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(configManager.getMessage("messages.login-usage", "&cUsage: /login <password>"));
            return true;
        }

        char[] password = args[0].toCharArray();
        String ipStr = (player.getAddress() != null && player.getAddress().getAddress() != null)
                ? player.getAddress().getAddress().getHostAddress()
                : "0.0.0.0";
        byte[] ipHash = ipHashService.hashIp(ipStr);

        authService.login(uuid, player.getName(), password, ipHash).thenAccept(result -> {
            Arrays.fill(password, ' ');
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                switch (result) {
                    case SUCCESS -> {
                        restrictionManager.setPlayerAuthenticated(uuid);
                        player.sendMessage(configManager.getMessage("messages.login-success", "&aSuccessfully logged in!"));
                    }
                    case INVALID_PASSWORD, ACCOUNT_NOT_FOUND ->
                            player.sendMessage(configManager.getMessage("messages.login-failed", "&cInvalid username or password."));
                    case ACCOUNT_LOCKED ->
                            player.sendMessage(configManager.getMessage("messages.account-locked", "&cAccount is temporarily locked."));
                    case RATE_LIMITED ->
                            player.sendMessage(configManager.getMessage("messages.rate-limited", "&cToo many attempts. Please wait."));
                    default ->
                            player.sendMessage(configManager.getMessage("messages.error", "&cAn internal error occurred."));
                }
            });
        }).exceptionally(ex -> {
            Arrays.fill(password, ' ');
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(configManager.getMessage("messages.error", "&cAn internal error occurred."));
                }
            });
            plugin.getLogger().severe("Error processing login for " + player.getName() + ": " + ex.getMessage());
            return null;
        });

        return true;
    }
}
