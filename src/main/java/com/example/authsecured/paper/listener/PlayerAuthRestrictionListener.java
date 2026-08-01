package com.example.authsecured.paper.listener;

import com.example.authsecured.infrastructure.config.ConfigManager;
import com.example.authsecured.paper.player.PlayerRestrictionManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

public class PlayerAuthRestrictionListener implements Listener {

    private final PlayerRestrictionManager restrictionManager;
    private final ConfigManager configManager;

    public PlayerAuthRestrictionListener(PlayerRestrictionManager restrictionManager, ConfigManager configManager) {
        this.restrictionManager = restrictionManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!restrictionManager.isAuthenticated(player.getUniqueId())) {
            if (configManager.getBoolean("restrictions.freeze-player", true)) {
                Location from = event.getFrom();
                Location to = event.getTo();
                if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                    Location loc = restrictionManager.getInitialLocation(player.getUniqueId());
                    if (loc != null) {
                        event.setTo(loc);
                    } else {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!restrictionManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            if (configManager.getBoolean("restrictions.block-chat", true)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(configManager.getMessage("messages.must-auth", "&cYou must authenticate first!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!restrictionManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            if (configManager.getBoolean("restrictions.block-block-break", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!restrictionManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            if (configManager.getBoolean("restrictions.block-block-place", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && !restrictionManager.isAuthenticated(player.getUniqueId())) {
            if (configManager.getBoolean("restrictions.block-inventory", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && !restrictionManager.isAuthenticated(player.getUniqueId())) {
            if (configManager.getBoolean("restrictions.block-inventory", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!restrictionManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            if (configManager.getBoolean("restrictions.block-item-drop", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemPickup(PlayerAttemptPickupItemEvent event) {
        if (!restrictionManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            if (configManager.getBoolean("restrictions.block-item-pickup", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!restrictionManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            if (configManager.getBoolean("restrictions.block-interactions", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && !restrictionManager.isAuthenticated(player.getUniqueId())) {
            if (configManager.getBoolean("restrictions.block-damage-taken", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && !restrictionManager.isAuthenticated(player.getUniqueId())) {
            if (configManager.getBoolean("restrictions.block-damage-dealt", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && !restrictionManager.isAuthenticated(player.getUniqueId())) {
            if (configManager.getBoolean("restrictions.block-hunger", true)) {
                event.setCancelled(true);
            }
        }
    }
}
