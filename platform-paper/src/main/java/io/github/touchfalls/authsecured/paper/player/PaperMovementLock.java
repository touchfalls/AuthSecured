package io.github.touchfalls.authsecured.paper.player;

import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import io.github.touchfalls.authsecured.paper.scheduler.PaperScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops client-side movement before authentication by setting walk and fly speed
 * to zero, so the player does not walk away and get pulled back. Server-side
 * position checks in the restriction listener remain the authoritative guard.
 * Must be called on the server thread.
 */
public final class PaperMovementLock implements PlayerRestrictionManager.MovementLock {

    private static final float DEFAULT_WALK_SPEED = 0.2f;
    private static final float DEFAULT_FLY_SPEED = 0.1f;

    private record Speeds(float walk, float fly) {}

    private final ConfigManager configManager;
    private final PaperScheduler scheduler;
    private final Map<UUID, Speeds> savedSpeeds = new ConcurrentHashMap<>();

    public PaperMovementLock(ConfigManager configManager, PaperScheduler scheduler) {
        this.configManager = configManager;
        this.scheduler = scheduler;
    }

    @Override
    public void lock(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !configManager.getBoolean("restrictions.freeze-player", true)) return;
        // A crash while frozen persists zero speed in player data; never restore zero.
        savedSpeeds.computeIfAbsent(playerUuid, ignored -> new Speeds(
                positiveOr(player.getWalkSpeed(), DEFAULT_WALK_SPEED),
                positiveOr(player.getFlySpeed(), DEFAULT_FLY_SPEED)));
        if (player.isInsideVehicle()) player.leaveVehicle();
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);
    }

    @Override
    public void unlock(UUID playerUuid) {
        Speeds speeds = savedSpeeds.remove(playerUuid);
        if (speeds == null) return;
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) return;
        player.setWalkSpeed(speeds.walk());
        player.setFlySpeed(speeds.fly());
    }

    /** Restores every frozen online player, used when the plugin is disabled. */
    public void unlockAll() {
        for (UUID playerUuid : Map.copyOf(savedSpeeds).keySet()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) scheduler.runOrSchedule(player, () -> unlock(playerUuid));
        }
    }

    private static float positiveOr(float value, float fallback) {
        return value > 0f ? value : fallback;
    }
}
