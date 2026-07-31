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

public class RegisterCommand implements CommandExecutor {

    private final AuthService authService;
    private final PlayerRestrictionManager restrictionManager;
    private final ConfigManager configManager;
    private final IpHashService ipHashService;
    private final Plugin plugin;

    public RegisterCommand(AuthService authService, PlayerRestrictionManager restrictionManager,
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
            player.sendMessage(configManager.getMessage("messages.already-authenticated", "&cYou are already authenticated!"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(configManager.getMessage("messages.register-usage", "&cUsage: /register <password> <confirmPassword>"));
            return true;
        }

        if (!args[0].equals(args[1])) {
            player.sendMessage(configManager.getMessage("messages.passwords-dont-match", "&cPasswords do not match!"));
            return true;
        }

        char[] password = args[0].toCharArray();
        String ipStr = (player.getAddress() != null && player.getAddress().getAddress() != null)
                ? player.getAddress().getAddress().getHostAddress()
                : "0.0.0.0";
        byte[] ipHash = ipHashService.hashIp(ipStr);

        authService.register(uuid, player.getName(), password, ipHash).thenAccept(result -> {
            Arrays.fill(password, ' ');
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                switch (result) {
                    case SUCCESS -> {
                        restrictionManager.setPlayerAuthenticated(uuid);
                        player.sendMessage(configManager.getMessage("messages.register-success", "&aSuccessfully registered and logged in!"));
                    }
                    case ALREADY_REGISTERED ->
                            player.sendMessage(configManager.getMessage("messages.already-registered", "&cAccount or username already registered."));
                    case VALIDATION_FAILED ->
                            player.sendMessage(configManager.getMessage("messages.password-policy-failed", "&cPassword does not meet length/complexity policy."));
                    case RATE_LIMITED ->
                            player.sendMessage(configManager.getMessage("messages.rate-limited", "&cRegistration rate limit reached for this IP."));
                    default ->
                            player.sendMessage(configManager.getMessage("messages.error", "&cAn internal error occurred."));
                }
            });
        });

        return true;
    }
}
