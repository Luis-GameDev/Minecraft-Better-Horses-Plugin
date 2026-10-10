package me.luisgamedev.betterhorses.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The only scheduling gateway used by BetterHorses. Entity work follows entities,
 * block work follows its owning region, and global/async work is explicit.
 */
public final class BetterHorsesScheduler {
    private final Plugin plugin;
    private final TaskRegistry registry = new TaskRegistry();

    public BetterHorsesScheduler(Plugin plugin) { this.plugin = Objects.requireNonNull(plugin); }

    public ScheduledTaskHandle entity(Entity entity, long delayTicks, Consumer<ScheduledTask> action, Runnable retired) {
        ScheduledTask task = entity.getScheduler().runDelayed(plugin, action, retired, Math.max(1L, delayTicks));
        return task == null ? ScheduledTaskHandle.rejected() : registry.own(ScheduledTaskHandle.of(task));
    }

    public ScheduledTaskHandle entityRepeating(Entity entity, long delayTicks, long periodTicks,
                                                Consumer<ScheduledTask> action, Runnable retired) {
        ScheduledTask task = entity.getScheduler().runAtFixedRate(plugin, action, retired,
                Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return task == null ? ScheduledTaskHandle.rejected() : registry.own(ScheduledTaskHandle.of(task));
    }

    public ScheduledTaskHandle region(Location location, long delayTicks, Consumer<ScheduledTask> action) {
        ScheduledTask task = plugin.getServer().getRegionScheduler().runDelayed(plugin, location, action,
                Math.max(1L, delayTicks));
        return registry.own(ScheduledTaskHandle.of(task));
    }

    public ScheduledTaskHandle global(Consumer<ScheduledTask> action) {
        return registry.own(ScheduledTaskHandle.of(plugin.getServer().getGlobalRegionScheduler().run(plugin, action)));
    }

    public ScheduledTaskHandle async(long delayMillis, Consumer<ScheduledTask> action) {
        return registry.own(ScheduledTaskHandle.of(plugin.getServer().getAsyncScheduler().runDelayed(
                plugin, action, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS)));
    }

    public void shutdown() { registry.cancelAll(); }

    public int ownedTaskCount() { return registry.size(); }
}
