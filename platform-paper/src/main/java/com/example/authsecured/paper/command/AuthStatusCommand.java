package com.example.authsecured.paper.command;

import com.example.authsecured.paper.player.PlayerRestrictionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AuthStatusCommand implements CommandExecutor {

    private final PlayerRestrictionManager restrictionManager;

    public AuthStatusCommand(PlayerRestrictionManager restrictionManager) {
        this.restrictionManager = restrictionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        boolean authenticated = restrictionManager.isAuthenticated(player.getUniqueId());
        if (authenticated) {
            player.sendMessage("§aAuth Status: AUTHENTICATED");
        } else {
            player.sendMessage("§cAuth Status: AUTH_REQUIRED");
        }
        return true;
    }
}
