package io.github.touchfalls.authsecured.paper.listener;

import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import io.github.touchfalls.authsecured.paper.player.PlayerRestrictionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class PlayerCommandListener implements Listener {

    private final PlayerRestrictionManager restrictionManager;
    private final ConfigManager configManager;
    private record AllowedCommands(List<String> source, Set<String> commands) {}
    private volatile AllowedCommands allowed = new AllowedCommands(List.of(), Set.of());

    private static final List<String> DEFAULT_ALLOWED = List.of(
            "/login", "/l",
            "/register", "/reg",
            "/changepassword", "/cp"
    );

    public PlayerCommandListener(PlayerRestrictionManager restrictionManager, ConfigManager configManager) {
        this.restrictionManager = restrictionManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (restrictionManager.isAuthenticated(player.getUniqueId())) {
            return;
        }

        if (!configManager.getBoolean("restrictions.block-commands", true)) {
            return;
        }

        String message = event.getMessage().trim().toLowerCase();
        String command = message.split(" ")[0];

        if (!allowedCommands().contains(command)) {
            event.setCancelled(true);
            player.sendMessage(configManager.getMessage("messages.command-blocked", "&cYou must authenticate before using this command!"));
        }
    }

    /**
     * Before login the client only learns about the commands it may run, so tab completion
     * and the command list do not reveal the rest of the server's commands. The full list
     * is sent again once the player authenticates.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandListSend(PlayerCommandSendEvent event) {
        if (isRestricted(event.getPlayer())) {
            retainAllowedCommands(event.getCommands(), allowedCommands());
        }
    }

    /** Clients that ask for suggestions anyway get none for commands they may not run yet. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        if (event.getSender() instanceof Player player && isRestricted(player)) {
            String command = event.getBuffer().stripLeading().toLowerCase(Locale.ROOT).split(" ", 2)[0];
            if (!allowedCommands().contains(command.startsWith("/") ? command : "/" + command)) {
                event.setCancelled(true);
            }
        }
    }

    private boolean isRestricted(Player player) {
        return !restrictionManager.isAuthenticated(player.getUniqueId())
                && configManager.getBoolean("restrictions.block-commands", true);
    }

    private Set<String> allowedCommands() {
        List<String> source = configManager.getStringList("restrictions.allowed-commands", DEFAULT_ALLOWED);
        AllowedCommands current = allowed;
        if (!source.equals(current.source())) {
            // Rebuilt only after a reload changed the list, not on every command or tab request.
            current = new AllowedCommands(source, source.stream()
                    .map(command -> command.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet()));
            allowed = current;
        }
        return current.commands();
    }

    /** Keeps only labels on the allow-list, which holds labels with their leading slash. */
    static void retainAllowedCommands(Collection<String> labels, Set<String> allowedCommands) {
        labels.removeIf(label -> !allowedCommands.contains("/" + label.toLowerCase(Locale.ROOT)));
    }
}
