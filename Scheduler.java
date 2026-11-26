package com.grin;

import java.util.ArrayList;
import java.util.List;

/** Tiny "run this later" helper, ticked from the server tick event. */
public final class Scheduler {
    private static final class Task {
        int left;
        final Runnable run;
        Task(int left, Runnable run) { this.left = left; this.run = run; }
    }

    private static final List<Task> TASKS = new ArrayList<>();

    private Scheduler() {}

    public static void later(int ticks, Runnable run) {
        TASKS.add(new Task(ticks, run));
    }

    public static void tick() {
        if (TASKS.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        for (Task t : new ArrayList<>(TASKS)) {
            if (--t.left <= 0) due.add(t);
        }
        TASKS.removeAll(due);
        for (Task t : due) {
            try {
                t.run.run();
            } catch (Exception e) {
                GrinMod.LOGGER.error("Scheduled task failed", e);
            }
        }
    }

    public static void clear() {
        TASKS.clear();
    }
}
