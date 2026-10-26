package io.github.touchfalls.authsecured.paper.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Single server thread: Paper, Purpur, Spigot and Leaf. */
final class BukkitServerScheduler implements PaperScheduler {

    private final Plugin plugin;

    BukkitServerScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void schedule(Player player, Runnable task) {
        scheduleGlobal(task);
    }

    @Override
    public void runOrSchedule(Player player, Runnable task) {
        runOrScheduleGlobal(task);
    }

    @Override
    public void scheduleGlobal(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runOrScheduleGlobal(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            scheduleGlobal(task);
        }
    }

    @Override
    public void scheduleGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    @Override
    public void cancelTasks() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
