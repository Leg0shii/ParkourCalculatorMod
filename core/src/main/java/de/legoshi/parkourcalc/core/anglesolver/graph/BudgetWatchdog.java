package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.util.concurrent.atomic.AtomicBoolean;

public final class BudgetWatchdog implements Runnable {

    private final AtomicBoolean outer;
    private volatile AtomicBoolean current;
    private volatile long deadlineNanos;
    private volatile boolean done;
    private final Thread thread;
    private boolean started;

    public BudgetWatchdog(AtomicBoolean outer) {
        this.outer = outer;
        this.thread = new Thread(this, "solver-graph-watchdog");
        this.thread.setDaemon(true);
    }

    public AtomicBoolean arm(long deadline) {
        if (!started) {
            started = true;
            thread.start();
        }
        current = null;
        AtomicBoolean token = new AtomicBoolean(false);
        deadlineNanos = deadline;
        current = token;
        return token;
    }

    public void disarm() {
        current = null;
    }

    public void shutdown() {
        done = true;
        thread.interrupt();
    }

    @Override
    public void run() {
        while (!done) {
            AtomicBoolean c = current;
            if (c != null && (outer.get() || (deadlineNanos > 0 && System.nanoTime() > deadlineNanos))) {
                c.set(true);
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
