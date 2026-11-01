package me.luisgamedev.betterhorses.listeners;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import me.luisgamedev.betterhorses.growing.HorseGrowthManager;
import me.luisgamedev.betterhorses.scheduler.ScheduledTaskHandle;
import org.bukkit.entity.Horse;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static me.luisgamedev.betterhorses.listeners.TraitLifecycleRegressionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class GrowthSchedulingRegressionTest {
    @Test void synchronousRetiredSchedulerDoesNotRecursivelyMutateTheTaskMap() throws Exception {
        try (Fixture f = new Fixture()) {
            HorseGrowthManager manager = new HorseGrowthManager(f.plugin);
            EntityScheduler retired = proxy(EntityScheduler.class, (self, method, args) -> null);
            Horse horse = horse(f, retired);
            assertDoesNotThrow(() -> manager.track(horse));
            assertTrue(tasks(manager).isEmpty());
            assertEquals(0, f.plugin.scheduler().ownedTaskCount());
        }
    }

    @Test void retirementDuringSubmissionCancelsTheLateReturnedHandle() throws Exception {
        try (Fixture f = new Fixture()) {
            HorseGrowthManager manager = new HorseGrowthManager(f.plugin);
            Task nativeTask = f.addTask(() -> {}, 1, 20);
            EntityScheduler scheduler = proxy(EntityScheduler.class, (self, method, args) -> {
                ((Runnable) args[2]).run();
                return nativeTask.paper;
            });
            assertDoesNotThrow(() -> manager.track(horse(f, scheduler)));
            assertTrue(tasks(manager).isEmpty());
            assertTrue(nativeTask.cancelled, "retirement before attach must still cancel the submitted work");
        }
    }

    @Test void trackingIsIdempotentAndLateRetirementCannotRemoveAReplacement() throws Exception {
        try (Fixture f = new Fixture()) {
            HorseGrowthManager manager = new HorseGrowthManager(f.plugin);
            List<Runnable> retirement = new ArrayList<>();
            List<Task> nativeTasks = new ArrayList<>();
            AtomicInteger submissions = new AtomicInteger();
            EntityScheduler scheduler = proxy(EntityScheduler.class, (self, method, args) -> {
                submissions.incrementAndGet();
                retirement.add((Runnable) args[2]);
                Task nativeTask = f.addTask(() -> {}, 1, 20);
                nativeTasks.add(nativeTask);
                return nativeTask.paper;
            });
            Horse horse = horse(f, scheduler);
            manager.track(horse);
            manager.track(horse);
            assertEquals(1, submissions.get());
            ScheduledTaskHandle first = (ScheduledTaskHandle) tasks(manager).values().iterator().next();
            first.cancel();
            manager.track(horse);
            Object replacement = tasks(manager).get(horse.getUniqueId());
            retirement.getFirst().run();
            assertSame(replacement, tasks(manager).get(horse.getUniqueId()));
            assertEquals(2, submissions.get());
            assertFalse(nativeTasks.get(1).cancelled);
            manager.shutdown();
            assertTrue(tasks(manager).isEmpty());
            assertTrue(nativeTasks.get(1).cancelled);
        }
    }

    @Test void shutdownDuringSubmissionCancelsItsLateHandleAndPreventsNewTracking() throws Exception {
        try (Fixture f = new Fixture()) {
            HorseGrowthManager manager = new HorseGrowthManager(f.plugin);
            Task nativeTask = f.addTask(() -> {}, 1, 20);
            AtomicInteger submissions = new AtomicInteger();
            EntityScheduler scheduler = proxy(EntityScheduler.class, (self, method, args) -> {
                submissions.incrementAndGet();
                manager.shutdown();
                return nativeTask.paper;
            });
            Horse horse = horse(f, scheduler);
            manager.track(horse);
            assertTrue(tasks(manager).isEmpty());
            assertTrue(nativeTask.cancelled);
            manager.track(horse);
            assertEquals(1, submissions.get());
        }
    }

    @Test void throwingSubmissionDoesNotKeepAnUnusableTrackingEntry() throws Exception {
        try (Fixture f = new Fixture()) {
            HorseGrowthManager manager = new HorseGrowthManager(f.plugin);
            f.failScheduling = true;
            assertThrows(IllegalPluginAccessException.class, () -> manager.track(f.horse.handle));
            assertTrue(tasks(manager).isEmpty());
            f.failScheduling = false;
            manager.track(f.horse.handle);
            assertEquals(1, tasks(manager).size());
            manager.shutdown();
        }
    }

    private static Horse horse(Fixture f, EntityScheduler scheduler) {
        return proxy(Horse.class, (self, method, args) -> method.getName().equals("getScheduler")
                ? scheduler : f.horse.invoke(self, method, args));
    }

    private static Map<?, ?> tasks(HorseGrowthManager manager) throws Exception {
        return (Map<?, ?>) field(HorseGrowthManager.class, "tasks").get(manager);
    }
}
