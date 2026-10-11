package me.luisgamedev.betterhorses.shaders;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Standard Paper implementation: context reads and effect mutations run on the server thread. */
final class ShaderPlayerScheduler {
    private final BetterHorses plugin;

    ShaderPlayerScheduler(BetterHorses plugin) { this.plugin = plugin; }

    Runnable repeat(Player player, long period, Runnable action, Runnable retired) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, action, 1L, period);
        return task::cancel;
    }

    void execute(Player player, Runnable action, Runnable retired) {
        Bukkit.getScheduler().runTask(plugin, action);
    }

    void cleanupOnShutdown(Player player, Runnable action) { action.run(); }
}
