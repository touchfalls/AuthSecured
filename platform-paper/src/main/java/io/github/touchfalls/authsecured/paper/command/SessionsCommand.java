package io.github.touchfalls.authsecured.paper.command;

import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import io.github.touchfalls.authsecured.ports.UnifiedCommandExecutor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Parses {@code /sessions [revoke]}; the behaviour lives in {@link UnifiedCommandExecutor}, shared with Fabric. */
public class SessionsCommand implements CommandExecutor {

    private final UnifiedCommandExecutor commandExecutor;
    private final ConfigManager configManager;

    public SessionsCommand(UnifiedCommandExecutor commandExecutor, ConfigManager configManager) {
        this.commandExecutor = commandExecutor;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage(
                    "messages.only-players", "&cOnly players can use this command."));
            return true;
        }
        if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("revoke"))) {
            return false;
        }
        commandExecutor.executeSessions(player.getUniqueId(), args.length == 1);
        return true;
    }
}
