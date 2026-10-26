package io.github.touchfalls.authsecured.paper.scheduler;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Runs work on the thread that owns it. Paper, Purpur, Spigot and Leaf tick everything on one
 * server thread; Folia ticks each region on its own thread and has no main thread, so player
 * state may only change on the player's region and server-wide work runs on the global region.
 */
public interface PaperScheduler {

    static PaperScheduler create(Plugin plugin) {
        // FoliaScheduler uses Paper-only API and is only loaded when Folia is running.
        return isFolia() ? new FoliaScheduler(plugin) : new BukkitServerScheduler(plugin);
    }

    static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    /** Runs the task on the player's thread next tick; on Folia it is dropped if the player has left. */
    void schedule(Player player, Runnable task);

    /** Runs the task right away if this thread owns the player, otherwise like {@link #schedule(Player, Runnable)}. */
    void runOrSchedule(Player player, Runnable task);

    /** Runs a task that belongs to no single player next tick on the server or global region thread. */
    void scheduleGlobal(Runnable task);

    /** Runs the task right away if this is the server thread, otherwise like {@link #scheduleGlobal}. */
    void runOrScheduleGlobal(Runnable task);

    void scheduleGlobalTimer(Runnable task, long delayTicks, long periodTicks);

    void cancelTasks();

    /** Replies to players on their own thread and to the console or RCON on the global one. */
    default void schedule(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            schedule(player, task);
        } else {
            scheduleGlobal(task);
        }
    }
}
