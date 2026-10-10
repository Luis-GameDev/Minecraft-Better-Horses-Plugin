package me.luisgamedev.betterhorses.listeners;

import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ListenerRegistrationManagerTest {
    @Test
    void disabledListenerIsNotCreatedOrRegistered() {
        AtomicInteger creations = new AtomicInteger();
        Harness harness = new Harness();
        harness.manager.define("optional", () -> {
            creations.incrementAndGet();
            return new TestListener();
        }, () -> false);

        harness.manager.synchronize();

        assertEquals(0, creations.get());
        assertTrue(harness.active.isEmpty());
    }

    @Test
    void reloadTransitionsRegisterAndUnregisterTheExactInstance() {
        AtomicBoolean enabled = new AtomicBoolean(false);
        Harness harness = new Harness();
        harness.manager.define("optional", TestListener::new, enabled::get);

        harness.manager.synchronize();
        enabled.set(true);
        harness.manager.synchronize();
        Listener registered = harness.manager.registeredListener("optional");
        assertNotNull(registered);
        assertSame(registered, harness.active.getFirst());

        enabled.set(false);
        harness.manager.synchronize();
        assertTrue(harness.active.isEmpty());
        assertFalse(harness.manager.isRegistered("optional"));
        assertEquals(1, ((TestListener) registered).shutdowns);
    }

    @Test
    void oneHundredUnchangedReloadsKeepOneInstanceAndRegistration() {
        AtomicInteger creations = new AtomicInteger();
        Harness harness = new Harness();
        harness.manager.define("always", () -> {
            creations.incrementAndGet();
            return new TestListener();
        }, () -> true);

        for (int i = 0; i < 100; i++) harness.manager.synchronize();

        assertEquals(1, creations.get());
        assertEquals(1, harness.registrations);
        assertEquals(1, harness.active.size());
        assertEquals(99, ((TestListener) harness.active.getFirst()).reloads);
    }

    @Test
    void rapidTogglesNeverLeaveDuplicatesOrStaleReferences() {
        AtomicBoolean enabled = new AtomicBoolean();
        Harness harness = new Harness();
        harness.manager.define("toggle", TestListener::new, enabled::get);

        for (int i = 0; i < 100; i++) {
            enabled.set(i % 2 == 0);
            harness.manager.synchronize();
            assertEquals(enabled.get() ? 1 : 0, harness.active.size());
            assertEquals(enabled.get(), harness.manager.isRegistered("toggle"));
        }
    }

    @Test
    void multiFeatureListenerRemainsUntilEveryDependencyIsDisabled() {
        AtomicBoolean first = new AtomicBoolean(true);
        AtomicBoolean second = new AtomicBoolean(false);
        Harness harness = new Harness();
        harness.manager.define("multi", TestListener::new, () -> first.get() || second.get());

        harness.manager.synchronize();
        Listener original = harness.active.getFirst();
        first.set(false);
        second.set(true);
        harness.manager.synchronize();
        assertSame(original, harness.active.getFirst());
        first.set(false);
        second.set(false);
        harness.manager.synchronize();
        assertTrue(harness.active.isEmpty());
    }

    @Test
    void shutdownUnregistersOnlyOwnedInstancesAndRunsCleanup() {
        Harness harness = new Harness();
        TestListener foreign = new TestListener();
        harness.active.add(foreign);
        harness.manager.define("core", TestListener::new, () -> true);
        harness.manager.synchronize();
        TestListener owned = (TestListener) harness.manager.registeredListener("core");

        harness.manager.shutdown();

        assertEquals(List.of(foreign), harness.active);
        assertEquals(1, owned.shutdowns);
        assertEquals(0, harness.manager.registeredCount());
    }

    private static final class Harness {
        private final List<Listener> active = new ArrayList<>();
        private int registrations;
        private final ListenerRegistrationManager manager = new ListenerRegistrationManager(listener -> {
            registrations++;
            active.add(listener);
        }, active::remove);
    }

    private static final class TestListener implements Listener, ListenerLifecycle {
        private int reloads;
        private int shutdowns;

        @Override
        public void onConfigurationReload() {
            reloads++;
        }

        @Override
        public void shutdown() {
            shutdowns++;
        }
    }
}
