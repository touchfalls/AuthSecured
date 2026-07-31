package com.example.authsecured.paper.listener;

import com.example.authsecured.infrastructure.config.ConfigManager;
import com.example.authsecured.paper.player.PlayerRestrictionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Set;

public class PlayerCommandListener implements Listener {

    private final PlayerRestrictionManager restrictionManager;
    private final ConfigManager configManager;

    private static final Set<String> ALLOWED_COMMANDS = Set.of(
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

        String message = event.getMessage().trim().toLowerCase();
        String command = message.split(" ")[0];

        if (!ALLOWED_COMMANDS.contains(command)) {
            event.setCancelled(true);
            player.sendMessage(configManager.getMessage("messages.command-blocked", "&cYou must authenticate before using this command!"));
        }
    }
}
