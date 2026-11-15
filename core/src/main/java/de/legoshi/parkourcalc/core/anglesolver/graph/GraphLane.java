package de.legoshi.parkourcalc.core.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.solver.SolveClock;

final class GraphLane {

    final GraphNode start;
    final GraphContext ctx;
    final Candidate in;
    final SolveClock clock;
    int stepIndex;
    final String chainAtStart;
    private final GraphRunner.Walk walk;
    private Thread thread;
    private volatile Candidate result;
    private volatile RuntimeException error;
    private volatile boolean aborted;

    GraphLane(GraphRunner.Walk walk, GraphNode start, GraphContext ctx, Candidate in, SolveClock clock,
              int stepIndex) {
        this.walk = walk;
        this.start = start;
        this.ctx = ctx;
        this.in = in;
        this.clock = clock;
        this.stepIndex = stepIndex;
        this.chainAtStart = ctx.chain();
    }

    void start() {
        thread = new Thread(this::run, "solver-lane-" + start.id);
        thread.setDaemon(true);
        thread.start();
    }

    void run() {
        SolveClock previous = clock == null ? null : clock.enter();
        try {
            result = GraphRunner.walkFrom(walk, ctx, start, in == null ? null : in.copy(), true);
        } catch (RuntimeException e) {
            if (!aborted) error = e;
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
        ctx.abortCurrentNode();
    }

    Candidate result() {
        return result;
    }

    RuntimeException error() {
        return error;
    }
}
