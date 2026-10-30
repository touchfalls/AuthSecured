package io.github.touchfalls.authsecured.paper.listener;

import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import io.github.touchfalls.authsecured.paper.player.PlayerRestrictionManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;

import java.util.Set;

public class PlayerAuthRestrictionListener implements Listener {

    private final PlayerRestrictionManager restrictionManager;
    private final ConfigManager configManager;

    public PlayerAuthRestrictionListener(PlayerRestrictionManager restrictionManager, ConfigManager configManager) {
        this.restrictionManager = restrictionManager;
        this.configManager = configManager;
    }

    private static final Set<String> BLOCKED_TELEPORT_CAUSES = Set.of(
            "ENDER_PEARL", "CHORUS_FRUIT", "CONSUMABLE_EFFECT", "NETHER_PORTAL", "END_PORTAL",
            "END_GATEWAY", "SPECTATE", "EXIT_BED");

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isFrozen(player)) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            // Any position change, including jumping and falling, is undone. Looking around stays allowed.
            Location anchor = restrictionManager.getInitialLocation(player.getUniqueId());
            if (anchor == null || to == null || !anchor.getWorld().equals(to.getWorld())) {
                event.setCancelled(true);
                return;
            }
            Location frozen = anchor.clone();
            frozen.setYaw(to.getYaw());
            frozen.setPitch(to.getPitch());
            event.setTo(frozen);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (isFrozen(event.getPlayer()) && BLOCKED_TELEPORT_CAUSES.contains(event.getCause().name())) {
            event.setCancelled(true);
        }
    }

    // PlayerPortalEvent extends PlayerTeleportEvent but has its own handler list, so the handler above never sees it.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player && isFrozen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player player && isFrozen(player)) {
            event.setCancelled(true);
        }
    }

    private boolean isFrozen(Player player) {
        return !restrictionManager.isAuthenticated(player.getUniqueId())
                && configManager.getBoolean("restrictions.freeze-player", true);
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
    public void onFlightToggle(PlayerToggleFlightEvent event) {
        if (!restrictionManager.isAuthenticated(event.getPlayer().getUniqueId())
                && !configManager.getBoolean("restrictions.allow-flight", false)) {
            event.setCancelled(true);
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

    // Dragging fires its own event with its own handler list, so onInventoryClick does not cover it.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
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
    public void onItemPickup(EntityPickupItemEvent event) {
        // Bukkit event (not Paper-only) so the whole listener also registers on Spigot.
        if (event.getEntity() instanceof Player player && !restrictionManager.isAuthenticated(player.getUniqueId())) {
            if (configManager.getBoolean("restrictions.block-item-pickup", true)) {
                event.setCancelled(true);
            }
        }
    }

    // Not ignoreCancelled: isCancelled() only reflects the block use, so an earlier plugin denying
    // the block would otherwise leave the item in hand (pearl, bucket, food) usable.
    @EventHandler(priority = EventPriority.LOWEST)
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
