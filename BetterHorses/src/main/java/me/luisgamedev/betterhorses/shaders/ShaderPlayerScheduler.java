package me.luisgamedev.betterhorses.shaders;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Standard implementation: all context reads and effect mutations run on the server thread. */
final class ShaderPlayerScheduler {
    private final BetterHorses plugin;
    ShaderPlayerScheduler(BetterHorses plugin) { this.plugin = plugin; }
    Runnable schedule(Player player, long delay, Runnable action, Runnable retired) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, action, Math.max(1L, delay));
        return task::cancel;
    }
    void execute(Player player, Runnable action, Runnable retired) { schedule(player, 1L, action, retired); }
    void cleanupOnShutdown(Player player, Runnable action) { action.run(); }
}
