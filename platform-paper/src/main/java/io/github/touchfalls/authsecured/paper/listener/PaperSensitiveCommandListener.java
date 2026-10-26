package io.github.touchfalls.authsecured.paper.listener;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.touchfalls.authsecured.util.SecretRedactor;
import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import io.github.touchfalls.authsecured.paper.player.PlayerRestrictionManager;
import io.github.touchfalls.authsecured.paper.scheduler.PaperScheduler;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Executes password-bearing player commands before normal dispatch. Canceling
 * the original event prevents Paper's command logger from receiving the secret.
 */
public final class PaperSensitiveCommandListener implements Listener {

    private static final List<String> DEFAULT_ALLOWED = List.of(
            "/login", "/l", "/register", "/reg", "/changepassword", "/cp");
    private static final List<String> PASSWORD_COMMANDS = List.of("login", "register", "changepassword");

    private final JavaPlugin plugin;
    private final PlayerRestrictionManager restrictionManager;
    private final ConfigManager configManager;
    private final PaperScheduler scheduler;

    public PaperSensitiveCommandListener(JavaPlugin plugin, PlayerRestrictionManager restrictionManager,
                                         ConfigManager configManager, PaperScheduler scheduler) {
        this.plugin = plugin;
        this.restrictionManager = restrictionManager;
        this.configManager = configManager;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSensitiveCommand(PlayerCommandPreprocessEvent event) {
        ParsedCommand parsed = parse(event.getMessage());
        if (parsed == null || !SecretRedactor.isPasswordCommand(parsed.label())) {
            return;
        }

        PluginCommand command = resolvePasswordCommand(parsed.label());
        if (command == null) {
            blockUnavailableAliasBeforeLogin(event, parsed.label());
            return;
        }
        CommandExecutor executor = command.getExecutor();
        if (executor == null) {
            return;
        }

        event.setMessage("/" + parsed.label() + " [REDACTED]");
        event.setCancelled(true);
        if (!isAllowedBeforeLogin(event.getPlayer(), parsed.label())) {
            event.getPlayer().sendMessage(configManager.getMessage("messages.command-blocked",
                    "&cYou must authenticate before using this command!"));
            return;
        }
        PluginCommand selectedCommand = command;
        scheduler.schedule(event.getPlayer(), () -> executor.onCommand(
                event.getPlayer(), selectedCommand, parsed.label(), parsed.arguments()));
    }

    /**
     * An alias from plugin.yml that another plugin registered first is not active and resolves
     * to that plugin. A player who has not logged in yet and types it means to log in, so the
     * password must not reach the other plugin.
     */
    @SuppressWarnings("deprecation") // getPluginMeta() is Paper-only; Spigot needs getDescription().
    private void blockUnavailableAliasBeforeLogin(PlayerCommandPreprocessEvent event, String label) {
        Player player = event.getPlayer();
        if (restrictionManager == null || restrictionManager.isAuthenticated(player.getUniqueId())) {
            return;
        }
        String canonical = declaredPasswordLabels(plugin.getDescription().getCommands()).get(label);
        if (canonical == null) {
            return;
        }
        event.setMessage("/" + label + " [REDACTED]");
        event.setCancelled(true);
        if (configManager != null) {
            player.sendMessage(configManager.getMessage("messages.command-alias-unavailable",
                    "&c/{alias} belongs to another plugin on this server. Use /{command} instead.",
                    Map.of("alias", label, "command", canonical)));
        }
    }

    /** Password commands bypass normal dispatch, so the pre-login allow-list is enforced here as well. */
    private boolean isAllowedBeforeLogin(Player player, String label) {
        if (restrictionManager == null || configManager == null
                || restrictionManager.isAuthenticated(player.getUniqueId())
                || !configManager.getBoolean("restrictions.block-commands", true)) {
            return true;
        }
        return isAllowed(label, configManager.getStringList("restrictions.allowed-commands", DEFAULT_ALLOWED));
    }

    static boolean isAllowed(String label, List<String> allowedCommands) {
        String command = "/" + label.toLowerCase(Locale.ROOT);
        return allowedCommands.stream().anyMatch(allowed -> allowed.toLowerCase(Locale.ROOT).equals(command));
    }

    private PluginCommand resolvePasswordCommand(String label) {
        for (String canonical : PASSWORD_COMMANDS) {
            PluginCommand command = plugin.getCommand(canonical);
            if (command != null && (canonical.equals(label)
                    || command.getAliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(label)))) {
                return command;
            }
        }
        return null;
    }

    /**
     * Maps every label plugin.yml declares for a password command, aliases included, to the
     * command's name, whether or not the alias could be registered.
     */
    public static Map<String, String> declaredPasswordLabels(Map<String, Map<String, Object>> commands) {
        Map<String, String> labels = new HashMap<>();
        for (String canonical : PASSWORD_COMMANDS) {
            labels.put(canonical, canonical);
            Map<String, Object> definition = commands == null ? null : commands.get(canonical);
            Object aliases = definition == null ? null : definition.get("aliases");
            if (aliases instanceof String alias) {
                labels.put(alias.toLowerCase(Locale.ROOT), canonical);
            } else if (aliases instanceof Collection<?> list) {
                for (Object alias : list) {
                    if (alias != null) labels.put(alias.toString().toLowerCase(Locale.ROOT), canonical);
                }
            }
        }
        return labels;
    }

    static ParsedCommand parse(String message) {
        if (message == null || !message.startsWith("/")) return null;
        String body = message.substring(1).strip();
        if (body.isEmpty()) return null;
        String[] parts = body.split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0) label = label.substring(namespace + 1);
        return new ParsedCommand(label, Arrays.copyOfRange(parts, 1, parts.length));
    }

    record ParsedCommand(String label, String[] arguments) {
    }
}
