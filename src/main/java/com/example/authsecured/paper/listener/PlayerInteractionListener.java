package com.example.authsecured.paper.listener;

import com.example.authsecured.paper.player.PlayerRestrictionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public class PlayerInteractionListener implements Listener {

    private final PlayerRestrictionManager restrictionManager;

    public PlayerInteractionListener(PlayerRestrictionManager restrictionManager) {
        this.restrictionManager = restrictionManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!restrictionManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (!restrictionManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
