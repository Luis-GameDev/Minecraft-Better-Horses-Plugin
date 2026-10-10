package me.luisgamedev.betterhorses.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRegistryTest {
    @Test
    void cancellationIsIdempotentAndClearsOwnership() {
        TaskRegistry registry = new TaskRegistry();
        FakeHandle first = new FakeHandle();
        FakeHandle second = new FakeHandle();
        registry.own(first);
        registry.own(second);

        registry.cancelAll();
        registry.cancelAll();

        assertTrue(first.cancelled);
        assertTrue(second.cancelled);
        assertEquals(0, registry.size());
    }

    @Test
    void completedTasksArePrunedWhenNewWorkIsOwned() {
        TaskRegistry registry = new TaskRegistry();
        FakeHandle finished = new FakeHandle();
        registry.own(finished);
        finished.done = true;
        registry.own(new FakeHandle());
        assertEquals(1, registry.size());
    }

    private static final class FakeHandle implements ScheduledTaskHandle {
        private boolean cancelled;
        private boolean done;
        @Override public void cancel() { cancelled = true; done = true; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public boolean isDone() { return done; }
    }
}
