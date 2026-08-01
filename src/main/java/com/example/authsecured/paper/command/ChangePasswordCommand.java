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

public class ChangePasswordCommand implements CommandExecutor {

    private final AuthService authService;
    private final PlayerRestrictionManager restrictionManager;
    private final ConfigManager configManager;
    private final IpHashService ipHashService;
    private final Plugin plugin;

    public ChangePasswordCommand(AuthService authService, PlayerRestrictionManager restrictionManager,
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
        if (!restrictionManager.isAuthenticated(uuid)) {
            player.sendMessage(configManager.getMessage("messages.must-auth", "&cYou must be logged in to change your password!"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(configManager.getMessage("messages.changepass-usage", "&cUsage: /changepassword <oldPassword> <newPassword>"));
            return true;
        }

        char[] oldPassword = args[0].toCharArray();
        char[] newPassword = args[1].toCharArray();
        String ipStr = (player.getAddress() != null && player.getAddress().getAddress() != null)
                ? player.getAddress().getAddress().getHostAddress()
                : "0.0.0.0";
        byte[] ipHash = ipHashService.hashIp(ipStr);

        authService.changePassword(uuid, oldPassword, newPassword, ipHash).thenAccept(result -> {
            Arrays.fill(oldPassword, ' ');
            Arrays.fill(newPassword, ' ');
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                switch (result) {
                    case SUCCESS ->
                            player.sendMessage(configManager.getMessage("messages.changepass-success", "&aPassword changed successfully!"));
                    case INVALID_PASSWORD ->
                            player.sendMessage(configManager.getMessage("messages.old-password-invalid", "&cOld password is incorrect."));
                    case VALIDATION_FAILED ->
                            player.sendMessage(configManager.getMessage("messages.password-policy-failed", "&cNew password does not meet policy requirements."));
                    default ->
                            player.sendMessage(configManager.getMessage("messages.error", "&cAn internal error occurred."));
                }
            });
        }).exceptionally(ex -> {
            Arrays.fill(oldPassword, ' ');
            Arrays.fill(newPassword, ' ');
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(configManager.getMessage("messages.error", "&cAn internal error occurred."));
                }
            });
            plugin.getLogger().severe("Error changing password for " + player.getName() + ": " + ex.getMessage());
            return null;
        });

        return true;
    }
}
