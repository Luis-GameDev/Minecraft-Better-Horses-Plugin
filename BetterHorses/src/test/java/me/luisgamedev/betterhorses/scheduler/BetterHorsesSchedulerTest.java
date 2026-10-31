package me.luisgamedev.betterhorses.scheduler;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BetterHorsesSchedulerTest {
    @Test void disabledPluginRejectsEntityWorkBeforeTouchingItsScheduler() {
        AtomicBoolean enabled = new AtomicBoolean(false);
        BetterHorsesScheduler scheduler = new BetterHorsesScheduler(plugin(enabled));
        AtomicInteger retired = new AtomicInteger();
        Entity entity = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, args) -> { throw new AssertionError("Disabled submission accessed entity: " + method); });
        ScheduledTaskHandle handle = scheduler.entity(entity, 1, ignored -> fail("action ran"), retired::incrementAndGet);
        assertTrue(handle.isCancelled());
        assertEquals(1, retired.get());
        assertEquals(0, scheduler.ownedTaskCount());
    }

    @Test void schedulerShutdownRejectsLaterWorkEvenWhilePluginStillEnabled() {
        BetterHorsesScheduler scheduler = new BetterHorsesScheduler(plugin(new AtomicBoolean(true)));
        scheduler.shutdown();
        Entity entity = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, args) -> { throw new AssertionError("Closed scheduler accessed entity: " + method); });
        AtomicInteger retired = new AtomicInteger();
        assertTrue(scheduler.entityRepeating(entity, 1, 1, ignored -> fail("action ran"), retired::incrementAndGet).isDone());
        assertEquals(1, retired.get());
    }

    @Test void disableRacingWithServerSubmissionReturnsRejectedInsteadOfAbortingTeardown() {
        AtomicBoolean enabled = new AtomicBoolean(true);
        BetterHorsesScheduler scheduler = new BetterHorsesScheduler(plugin(enabled));
        EntityScheduler underlying = (EntityScheduler) Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),
                new Class<?>[]{EntityScheduler.class}, (proxy, method, args) -> {
                    enabled.set(false);
                    throw new IllegalPluginAccessException("plugin disabled between check and submit");
                });
        AtomicInteger retired = new AtomicInteger();
        assertTrue(scheduler.entity(entity(underlying), 1, ignored -> fail("action ran"), retired::incrementAndGet).isCancelled());
        assertEquals(1, retired.get());
    }

    @Test void serverRejectionCannotInvokeTheRetirementCallbackTwice() {
        BetterHorsesScheduler scheduler = new BetterHorsesScheduler(plugin(new AtomicBoolean(true)));
        EntityScheduler underlying = (EntityScheduler) Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),
                new Class<?>[]{EntityScheduler.class}, (proxy, method, args) -> {
                    ((Runnable) args[2]).run();
                    return null;
                });
        AtomicInteger retired = new AtomicInteger();
        assertTrue(scheduler.entity(entity(underlying), 1, ignored -> fail("action ran"), retired::incrementAndGet).isDone());
        assertEquals(1, retired.get());
    }

    private static Plugin plugin(AtomicBoolean enabled) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isEnabled")) return enabled.get();
                    throw new AssertionError("Unexpected plugin access: " + method);
                });
    }

    private static Entity entity(EntityScheduler scheduler) {
        return (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getScheduler")) return scheduler;
                    throw new AssertionError("Unexpected entity access: " + method);
                });
    }
}
