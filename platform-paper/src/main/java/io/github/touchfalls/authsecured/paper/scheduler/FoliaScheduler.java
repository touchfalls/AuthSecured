package io.github.touchfalls.authsecured.paper.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Folia: player work on the player's region thread, everything else on the global region. */
final class FoliaScheduler implements PaperScheduler {

    private final Plugin plugin;

    FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void schedule(Player player, Runnable task) {
        // A player who has left retires their scheduler; there is nothing left to update then.
        player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    @Override
    public void runOrSchedule(Player player, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            task.run();
        } else {
            schedule(player, task);
        }
    }

    @Override
    public void scheduleGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public void runOrScheduleGlobal(Runnable task) {
        scheduleGlobal(task);
    }

    @Override
    public void scheduleGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(),
                Math.max(1, delayTicks), periodTicks);
    }

    @Override
    public void cancelTasks() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }
}
