package me.luisgamedev.betterhorses.scheduler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe lifecycle ownership for tasks created by independently ticking regions. */
public final class TaskRegistry {
    private final Set<ScheduledTaskHandle> tasks = ConcurrentHashMap.newKeySet();

    public ScheduledTaskHandle own(ScheduledTaskHandle task) {
        tasks.removeIf(ScheduledTaskHandle::isDone);
        if (!task.isCancelled()) tasks.add(task);
        return task;
    }

    public void forget(ScheduledTaskHandle task) { tasks.remove(task); }

    public void cancelAll() {
        tasks.forEach(ScheduledTaskHandle::cancel);
        tasks.clear();
    }

    public int size() { return tasks.size(); }
}
