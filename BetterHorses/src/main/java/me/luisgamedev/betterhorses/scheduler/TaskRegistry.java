package me.luisgamedev.betterhorses.scheduler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe lifecycle ownership for tasks created by independently ticking regions. */
public final class TaskRegistry {
    private final Set<ScheduledTaskHandle> tasks = ConcurrentHashMap.newKeySet();
    private boolean closed;

    public synchronized ScheduledTaskHandle own(ScheduledTaskHandle task) {
        if (closed) {
            task.cancel();
            return task;
        }
        tasks.removeIf(ScheduledTaskHandle::isDone);
        if (!task.isCancelled()) tasks.add(task);
        return task;
    }

    public void forget(ScheduledTaskHandle task) { tasks.remove(task); }

    public synchronized void cancelAll() {
        closed = true;
        tasks.forEach(ScheduledTaskHandle::cancel);
        tasks.clear();
    }

    public int size() { return tasks.size(); }
}
