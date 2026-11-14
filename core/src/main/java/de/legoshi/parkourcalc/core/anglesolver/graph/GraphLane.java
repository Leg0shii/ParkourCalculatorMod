package de.legoshi.parkourcalc.core.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.solver.SolveClock;

import java.util.concurrent.atomic.AtomicBoolean;

final class GraphLane {

    final GraphNode node;
    final GraphContext ctx;
    final Candidate in;
    final SolveClock clock;
    final int stepIndex;
    private final long baseWorkNanos;
    private Thread thread;
    private volatile NodeOutcome outcome;
    private volatile RuntimeException error;
    private volatile AtomicBoolean token;
    private volatile boolean aborted;

    GraphLane(GraphNode node, GraphContext ctx, Candidate in, SolveClock clock, long baseWorkNanos, int stepIndex) {
        this.node = node;
        this.ctx = ctx;
        this.in = in;
        this.clock = clock;
        this.baseWorkNanos = baseWorkNanos;
        this.stepIndex = stepIndex;
    }

    void start(NodeRuntime rt, long deadlineNanos, long budgetNanos) {
        thread = new Thread(() -> run(rt, deadlineNanos, budgetNanos), "solver-lane-" + node.id);
        thread.setDaemon(true);
        thread.start();
    }

    void run(NodeRuntime rt, long deadlineNanos, long budgetNanos) {
        SolveClock previous = clock == null ? null : clock.enter();
        try {
            Candidate input = in == null ? null : in.copy();
            AtomicBoolean t = ctx.beginNode(node, deadlineNanos, budgetNanos);
            token = t;
            if (aborted) t.set(true);
            Guarantee taken = null;
            Candidate out = input;
            try {
                NodeOutcome o = rt.execute(ctx, input, t, deadlineNanos);
                outcome = o;
                taken = o.branch;
                out = o.candidate;
            } catch (RuntimeException e) {
                if (aborted) return;
                if (ctx.cancel.get() || !t.get()) {
                    error = e;
                    return;
                }
                e.printStackTrace();
                taken = node.type.fallbackBranch != null ? node.type.fallbackBranch : Guarantee.NONE;
            } finally {
                ctx.endNode(node, taken);
            }
            ctx.runState.noteResult(node.id, clock == null ? -1L : baseWorkNanos + clock.elapsedNanos(),
                    clock == null ? null : clock.unitsSnapshot(), out);
        } finally {
            if (clock != null) SolveClock.restore(previous);
            ctx.shutdown();
        }
    }

    void await() {
        if (thread == null) return;
        boolean interrupted = false;
        while (true) {
            try {
                thread.join();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    void abort() {
        aborted = true;
        AtomicBoolean t = token;
        if (t != null) t.set(true);
    }

    NodeOutcome outcome() {
        return outcome;
    }

    RuntimeException error() {
        return error;
    }
}
