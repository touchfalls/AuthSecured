package io.github.touchfalls.authsecured.paper.listener;

import io.github.touchfalls.authsecured.paper.player.PlayerRestrictionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
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

    // Both events extend PlayerInteractEntityEvent but have their own handler lists.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!restrictionManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
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
