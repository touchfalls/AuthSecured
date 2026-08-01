package com.example.authsecured.paper.command;

import com.example.authsecured.application.AuthService;
import com.example.authsecured.infrastructure.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;

public class AuthAdminCommand implements CommandExecutor {

    private final AuthService authService;
    private final ConfigManager configManager;
    private final Plugin plugin;

    public AuthAdminCommand(AuthService authService, ConfigManager configManager, Plugin plugin) {
        this.authService = authService;
        this.configManager = configManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("authsecured.admin")) {
            sender.sendMessage(configManager.getMessage("messages.no-permission", "&cYou do not have permission to use admin commands."));
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "reload" -> {
                configManager.loadConfigurations();
                sender.sendMessage("§aConfigurations and localizations reloaded successfully.");
            }
            case "session" -> handleSessionCommand(sender, args);
            case "unregister" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /authadmin unregister <player>");
                    return true;
                }
                String target = args[1];
                authService.unregister(target).thenAccept(success -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            sender.sendMessage("§aUnregistered player: " + target);
                        } else {
                            sender.sendMessage("§cCould not unregister player or account not found.");
                        }
                    });
                }).exceptionally(ex -> {
                    sender.sendMessage(String.format("§cError unregistering player: %s", ex.getMessage()));
                    return null;
                });
            }
            case "unlock" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /authadmin unlock <player>");
                    return true;
                }
                String target = args[1];
                authService.unlockAccount(target).thenAccept(success -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            sender.sendMessage("§aUnlocked account for player: " + target);
                        } else {
                            sender.sendMessage("§cCould not unlock account or account not found.");
                        }
                    });
                }).exceptionally(ex -> {
                    sender.sendMessage(String.format("§cError unlocking account: %s", ex.getMessage()));
                    return null;
                });
            }
            case "resetpassword" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /authadmin resetpassword <player> <newPassword>");
                    return true;
                }
                String target = args[1];
                char[] newPass = args[2].toCharArray();
                authService.resetPassword(target, newPass).thenAccept(success -> {
                    Arrays.fill(newPass, ' ');
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            sender.sendMessage("§aPassword reset successfully for player: " + target);
                        } else {
                            sender.sendMessage("§cFailed to reset password. Check player name and password policy.");
                        }
                    });
                }).exceptionally(ex -> {
                    Arrays.fill(newPass, ' ');
                    sender.sendMessage(String.format("§cError resetting password: %s", ex.getMessage()));
                    return null;
                });
            }
            default -> sendUsage(sender);
        }

        return true;
    }

    private void handleSessionCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /authadmin session <gettimeout|settimeout <seconds>>");
            return;
        }
        String action = args[1].toLowerCase();
        if (action.equals("gettimeout")) {
            long currentTimeout = authService.getSessionService().getTimeoutSeconds();
            sender.sendMessage("§aCurrent session duration timeout: §e" + currentTimeout + " seconds §7(" + (currentTimeout / 60) + " minutes)");
        } else if (action.equals("settimeout")) {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /authadmin session settimeout <seconds>");
                return;
            }
            try {
                long newTimeout = Long.parseLong(args[2]);
                if (newTimeout < 0) {
                    sender.sendMessage("§cTimeout seconds must be a positive integer.");
                    return;
                }
                sender.sendMessage("§aSession duration timeout updated to: §e" + newTimeout + " seconds §7(" + (newTimeout / 60) + " minutes)");
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid number format for timeout seconds.");
            }
        } else {
            sender.sendMessage("§cUnknown session action. Use gettimeout or settimeout.");
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§e=== AuthSecured Admin Commands ===");
        sender.sendMessage("§e/authadmin reload §7- Reload plugin configuration and localizations");
        sender.sendMessage("§e/authadmin session gettimeout §7- View session auto-login timeout");
        sender.sendMessage("§e/authadmin session settimeout <seconds> §7- Set session timeout duration");
        sender.sendMessage("§e/authadmin unregister <player> §7- Delete account");
        sender.sendMessage("§e/authadmin unlock <player> §7- Unlock account lock");
        sender.sendMessage("§e/authadmin resetpassword <player> <newPassword> §7- Reset player password");
    }
}
