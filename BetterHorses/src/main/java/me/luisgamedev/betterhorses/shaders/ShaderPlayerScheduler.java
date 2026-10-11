package me.luisgamedev.betterhorses.shaders;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Folia implementation: all context reads and effect mutations follow the player. */
final class ShaderPlayerScheduler {
    private final BetterHorses plugin;
    ShaderPlayerScheduler(BetterHorses plugin) { this.plugin = plugin; }
    Runnable schedule(Player player, long delay, Runnable action, Runnable retired) {
        ScheduledTask task = player.getScheduler().runDelayed(plugin, ignored -> action.run(), retired, Math.max(1L, delay));
        if (task == null) { retired.run(); return null; }
        return () -> task.cancel();
    }
    void execute(Player player, Runnable action, Runnable retired) { schedule(player, 1L, action, retired); }
    void cleanupOnShutdown(Player player, Runnable action) {
        if (Bukkit.isOwnedByCurrentRegion(player)) action.run();
    }
}
