package me.luisgamedev.betterhorses.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Scheduling gateway: entity work follows its owner and disabled plugins never submit new work. */
public final class BetterHorsesScheduler {
    private final Plugin plugin;
    private final TaskRegistry registry = new TaskRegistry();
    private volatile boolean closed;

    public BetterHorsesScheduler(Plugin plugin) { this.plugin = Objects.requireNonNull(plugin); }

    public ScheduledTaskHandle entity(Entity entity, long delayTicks, Consumer<ScheduledTask> action, Runnable retired) {
        Runnable once = once(retired);
        return submit(() -> entity.getScheduler().runDelayed(plugin, guarded(action), once,
                Math.max(1L, delayTicks)), once);
    }

    public ScheduledTaskHandle entityRepeating(Entity entity, long delayTicks, long periodTicks,
                                                Consumer<ScheduledTask> action, Runnable retired) {
        Runnable once = once(retired);
        return submit(() -> entity.getScheduler().runAtFixedRate(plugin, guarded(action), once,
                Math.max(1L, delayTicks), Math.max(1L, periodTicks)), once);
    }

    public ScheduledTaskHandle region(Location location, long delayTicks, Consumer<ScheduledTask> action) {
        return submit(() -> plugin.getServer().getRegionScheduler().runDelayed(plugin, location, guarded(action),
                Math.max(1L, delayTicks)), () -> { });
    }

    public ScheduledTaskHandle global(Consumer<ScheduledTask> action) {
        return submit(() -> plugin.getServer().getGlobalRegionScheduler().run(plugin, guarded(action)), () -> { });
    }

    public ScheduledTaskHandle async(long delayMillis, Consumer<ScheduledTask> action) {
        return submit(() -> plugin.getServer().getAsyncScheduler().runDelayed(plugin, guarded(action),
                Math.max(0L, delayMillis), TimeUnit.MILLISECONDS), () -> { });
    }

    private ScheduledTaskHandle submit(Supplier<ScheduledTask> submission, Runnable rejected) {
        if (closed || !plugin.isEnabled()) {
            rejected.run();
            return ScheduledTaskHandle.rejected();
        }
        ScheduledTask task;
        try {
            task = submission.get();
        } catch (IllegalPluginAccessException exception) {
            // The plugin can be disabled between the eligibility check and the server submission.
            if (!closed && plugin.isEnabled()) throw exception;
            rejected.run();
            return ScheduledTaskHandle.rejected();
        }
        if (task == null) {
            rejected.run();
            return ScheduledTaskHandle.rejected();
        }
        return registry.own(ScheduledTaskHandle.of(task));
    }

    private Consumer<ScheduledTask> guarded(Consumer<ScheduledTask> action) {
        return task -> {
            if (!closed && plugin.isEnabled()) action.accept(task);
        };
    }

    private static Runnable once(Runnable retired) {
        AtomicBoolean called = new AtomicBoolean();
        return () -> {
            if (called.compareAndSet(false, true) && retired != null) retired.run();
        };
    }

    public void shutdown() {
        closed = true;
        registry.cancelAll();
    }

    public int ownedTaskCount() { return registry.size(); }
}
