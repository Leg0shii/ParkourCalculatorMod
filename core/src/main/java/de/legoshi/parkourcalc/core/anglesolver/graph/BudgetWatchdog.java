package de.legoshi.parkourcalc.core.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.solver.SolveClock;

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
        SolveClock clock = SolveClock.current();
        deadlineNanos = clock == null ? deadline : 0L;
        if (clock != null) clock.arm(token, deadline);
        current = token;
        return token;
    }

    public void disarm() {
        AtomicBoolean token = current;
        current = null;
        SolveClock clock = SolveClock.current();
        if (clock != null && token != null) clock.disarm(token);
    }

    public void shutdown() {
        disarm();
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
