package io.github.touchfalls.authsecured.paper.command;

import io.github.touchfalls.authsecured.bootstrap.AdminOperations;
import io.github.touchfalls.authsecured.bootstrap.DependencyContainer;
import io.github.touchfalls.authsecured.domain.auth.TemporaryPasswordReset;
import io.github.touchfalls.authsecured.paper.scheduler.PaperScheduler;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class AuthAdminCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of(
            "status", "doctor", "reload", "session", "audit", "unlock", "unregister", "resetpassword");

    private final DependencyContainer container;
    private final AdminOperations operations;
    private final Plugin plugin;
    private final PaperScheduler scheduler;

    public AuthAdminCommand(DependencyContainer container, Plugin plugin, PaperScheduler scheduler) {
        this.container = container;
        this.operations = new AdminOperations(container);
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (!hasPermission(sender, subCommand)) {
            sender.sendMessage(container.getConfigManager().getMessage(
                    "messages.no-permission", "§cYou do not have permission to use this command."));
            return true;
        }

        switch (subCommand) {
            case "status" -> operations.status().whenComplete((lines, error) -> sendResult(sender, lines, error));
            case "doctor" -> operations.doctor().whenComplete((lines, error) -> sendResult(sender, lines, error));
            case "reload" -> {
                boolean reloaded = container.reloadConfig();
                sender.sendMessage(message(reloaded ? "messages.admin-reloaded" : "messages.admin-reload-failed",
                        reloaded ? "&aConfiguration and localization reloaded."
                                : "&cReload rejected. The previous valid configuration is still active."));
            }
            case "session" -> handleSession(sender, args);
            case "audit" -> handleAudit(sender, args);
            case "unlock" -> handlePlayerBoolean(sender, args, "unlock",
                    target -> container.getAuthService().unlockAccount(target),
                    "messages.admin-account-unlocked", "&aAccount unlocked.");
            case "unregister" -> handlePlayerBoolean(sender, args, "unregister",
                    target -> container.getAuthService().unregister(target),
                    "messages.admin-account-unregistered", "&aAccount unregistered.");
            case "resetpassword" -> handleTemporaryPassword(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleSession(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(message("messages.admin-session-usage",
                    "&cUsage: /authadmin session <gettimeout|settimeout <seconds>>"));
            return;
        }
        if ("gettimeout".equalsIgnoreCase(args[1])) {
            long seconds = operations.getSessionTimeoutSeconds();
            sender.sendMessage(message("messages.admin-session-current",
                    "&aCurrent session timeout: &e{seconds} seconds.",
                    Map.of("seconds", Long.toString(seconds))));
            return;
        }
        if (!"settimeout".equalsIgnoreCase(args[1]) || args.length != 3) {
            sender.sendMessage(message("messages.admin-session-set-usage",
                    "&cUsage: /authadmin session settimeout <seconds>"));
            return;
        }
        try {
            long seconds = Long.parseLong(args[2]);
            operations.setSessionTimeoutSeconds(seconds);
            sender.sendMessage(message("messages.admin-session-changed",
                    "&aSession timeout changed immediately to &e{seconds} seconds&a. "
                            + "Update config.yml to keep it after restart.",
                    Map.of("seconds", Long.toString(seconds))));
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(message("messages.admin-session-invalid",
                    "&cTimeout must be an integer from 1 to 2592000 seconds."));
        }
    }

    private void handleAudit(CommandSender sender, String[] args) {
        int limit = 10;
        String player = null;
        try {
            if (args.length >= 2 && "player".equalsIgnoreCase(args[1])) {
                if (args.length < 3) {
                    sender.sendMessage(message("messages.admin-audit-player-usage",
                            "&cUsage: /authadmin audit player <name> [limit]"));
                    return;
                }
                player = args[2];
                if (args.length >= 4) limit = Integer.parseInt(args[3]);
            } else if (args.length >= 2) {
                limit = Integer.parseInt(args[1]);
            }
        } catch (NumberFormatException exception) {
            sender.sendMessage(message("messages.admin-audit-limit-invalid",
                    "&cAudit limit must be an integer from 1 to 100."));
            return;
        }
        if (limit < 1 || limit > 100) {
            sender.sendMessage(message("messages.admin-audit-limit-invalid",
                    "&cAudit limit must be an integer from 1 to 100."));
            return;
        }
        operations.audit(limit, player).whenComplete((lines, error) -> sendResult(sender, lines, error));
    }

    private void handleTemporaryPassword(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(message("messages.admin-reset-usage",
                    "&cUsage: /authadmin resetpassword <player>"));
            return;
        }
        if (!(sender instanceof Player)) {
            // The console and RCON senders write sendMessage() to the server log;
            // only an in-game sender can receive the password without logging it.
            sender.sendMessage(message("messages.admin-reset-console-blocked",
                    "&cRun this command in-game. The console and RCON cannot receive "
                            + "the password without writing it to the server log."));
            return;
        }
        operations.issueTemporaryPassword(args[1]).whenComplete((reset, error) ->
                scheduler.schedule(sender, () -> {
                    if (error != null) {
                        sender.sendMessage(message("messages.admin-reset-failed",
                                "&cTemporary password creation failed. Check the server log."));
                        plugin.getLogger().severe("Temporary password creation failed for " + args[1]);
                    } else if (reset == null) {
                        sender.sendMessage(message("messages.admin-account-not-found", "&cAccount not found."));
                    } else {
                        deliverTemporaryPassword(sender, args[1], reset);
                    }
                }));
    }

    private void deliverTemporaryPassword(CommandSender sender, String target, TemporaryPasswordReset reset) {
        char[] password = reset.copyPassword();
        try {
            String passwordText = new String(password);
            // BungeeCord chat components exist on Spigot and Paper; Adventure is Paper-only.
            TextComponent line = new TextComponent(TextComponent.fromLegacyText(message(
                    "messages.admin-temporary-password-prefix",
                    "&eTemporary password for &f{player}&e: ", Map.of("player", target))));
            TextComponent secret = new TextComponent(TextComponent.fromLegacyText(message(
                    "messages.admin-temporary-password-copy", "&f{password}", Map.of("password", passwordText))));
            secret.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, passwordText));
            secret.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(message(
                    "messages.admin-temporary-password-hover", "&aClick to copy the temporary password."))));
            line.addExtra(secret);
            sender.spigot().sendMessage(line);
            sender.sendMessage(message("messages.admin-temporary-password-expires",
                    "&7Expires at: {expires}. The player must use /changepassword <temporary> <new>.",
                    Map.of("expires", reset.getExpiresAt().toString())));
        } finally {
            Arrays.fill(password, '\0');
            reset.destroy();
        }
    }

    private void handlePlayerBoolean(CommandSender sender, String[] args, String usage,
                                     Function<String, CompletableFuture<Boolean>> action,
                                     String successKey, String successFallback) {
        if (args.length != 2) {
            sender.sendMessage(message("messages.admin-player-usage",
                    "&cUsage: /authadmin {operation} <player>", Map.of("operation", usage)));
            return;
        }
        action.apply(args[1]).whenComplete((success, error) ->
                scheduler.schedule(sender, () -> {
                    if (error != null) {
                        sender.sendMessage(message("messages.admin-operation-failed",
                                "&cOperation failed. Check the server log."));
                        plugin.getLogger().severe("AuthSecured admin operation failed: " + usage);
                    } else {
                        sender.sendMessage(Boolean.TRUE.equals(success)
                                ? message(successKey, successFallback)
                                : message("messages.admin-account-not-found", "&cAccount not found."));
                    }
                }));
    }

    private void sendResult(CommandSender sender, List<String> lines, Throwable error) {
        scheduler.schedule(sender, () -> {
            if (error != null) {
                sender.sendMessage(message("messages.admin-operation-failed",
                        "&cOperation failed. Check the server log."));
                plugin.getLogger().severe("AuthSecured administrative query failed.");
            } else {
                lines.forEach(sender::sendMessage);
            }
        });
    }

    private boolean hasPermission(CommandSender sender, String operation) {
        return sender.hasPermission("authsecured.admin")
                || sender.hasPermission("authsecured.admin." + operation);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS.stream().filter(value -> hasPermission(sender, value)).toList(), args[0]);
        }
        if (args.length == 2 && "session".equalsIgnoreCase(args[0])) {
            return filter(List.of("gettimeout", "settimeout"), args[1]);
        }
        if (args.length == 2 && "audit".equalsIgnoreCase(args[0])) {
            return filter(List.of("10", "25", "50", "player"), args[1]);
        }
        if (args.length == 3 && "audit".equalsIgnoreCase(args[0]) && "player".equalsIgnoreCase(args[1])) {
            return filter(playerNames(), args[2]);
        }
        if (args.length == 2 && List.of("unlock", "unregister", "resetpassword")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(playerNames(), args[1]);
        }
        return List.of();
    }

    private static List<String> playerNames() {
        List<String> names = new ArrayList<>();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null) names.add(player.getName());
        }
        return names;
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).sorted().toList();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(message("messages.admin-usage",
                "&e/authadmin <status|doctor|reload|session|audit|unlock|unregister|resetpassword>"));
    }

    private String message(String key, String fallback) {
        return container.getConfigManager().getMessage(key, fallback);
    }

    private String message(String key, String fallback, Map<String, String> placeholders) {
        return container.getConfigManager().getMessage(key, fallback, placeholders);
    }
}
