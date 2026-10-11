package me.luisgamedev.betterhorses.shaders;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Folia implementation: all context reads and effect mutations follow the player. */
final class ShaderPlayerScheduler {
    private final BetterHorses plugin;

    ShaderPlayerScheduler(BetterHorses plugin) { this.plugin = plugin; }

    Runnable repeat(Player player, long period, Runnable action, Runnable retired) {
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, ignored -> action.run(), retired, 1L, period);
        return task == null ? null : () -> task.cancel();
    }

    void execute(Player player, Runnable action, Runnable retired) {
        if (player.getScheduler().run(plugin, ignored -> action.run(), retired) == null) retired.run();
    }

    void cleanupOnShutdown(Player player, Runnable action) {
        // A disabled plugin cannot enqueue entity work. Never touch another region's player.
        if (Bukkit.isOwnedByCurrentRegion(player)) action.run();
    }
}
