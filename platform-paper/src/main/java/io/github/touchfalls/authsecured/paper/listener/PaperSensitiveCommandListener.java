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
import java.util.List;
import java.util.Locale;

/**
 * Executes password-bearing player commands before normal dispatch. Canceling
 * the original event prevents Paper's command logger from receiving the secret.
 */
public final class PaperSensitiveCommandListener implements Listener {

    private static final List<String> DEFAULT_ALLOWED = List.of(
            "/login", "/l", "/register", "/reg", "/changepassword", "/cp");

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
        CommandExecutor executor = command == null ? null : command.getExecutor();
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
        for (String canonical : java.util.List.of("login", "register", "changepassword")) {
            PluginCommand command = plugin.getCommand(canonical);
            if (command != null && (canonical.equals(label)
                    || command.getAliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(label)))) {
                return command;
            }
        }
        return null;
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
