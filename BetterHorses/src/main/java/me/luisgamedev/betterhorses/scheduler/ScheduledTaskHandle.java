package me.luisgamedev.betterhorses.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/** A small, platform-neutral ownership handle around Paper/Folia scheduled work. */
public interface ScheduledTaskHandle {
    void cancel();

    boolean isCancelled();

    boolean isDone();

    static ScheduledTaskHandle of(ScheduledTask task) {
        return new PaperTaskHandle(task);
    }

    static ScheduledTaskHandle rejected() {
        return RejectedTaskHandle.INSTANCE;
    }

    final class PaperTaskHandle implements ScheduledTaskHandle {
        private final ScheduledTask task;

        private PaperTaskHandle(ScheduledTask task) { this.task = task; }

        @Override public void cancel() { task.cancel(); }

        @Override public boolean isCancelled() {
            return task.getExecutionState() == ScheduledTask.ExecutionState.CANCELLED
                    || task.getExecutionState() == ScheduledTask.ExecutionState.CANCELLED_RUNNING;
        }
        @Override public boolean isDone() {
            return isCancelled() || task.getExecutionState() == ScheduledTask.ExecutionState.FINISHED;
        }
    }

    enum RejectedTaskHandle implements ScheduledTaskHandle {
        INSTANCE;
        @Override public void cancel() { }
        @Override public boolean isCancelled() { return true; }
        @Override public boolean isDone() { return true; }
    }
}
