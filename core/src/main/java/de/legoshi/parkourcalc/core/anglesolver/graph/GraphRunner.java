package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GraphRunner {

    public static final int MAX_NODE_VISITS = 1024;
    public static final long WRAP_RESERVE_MAX_NANOS = 3_000_000_000L;

    private GraphRunner() {
    }

    public static Candidate run(SolverGraph graph, GraphContext ctx) {
        try {
            return walk(graph, ctx);
        } finally {
            ctx.shutdown();
        }
    }

    private static Candidate walk(SolverGraph graph, GraphContext ctx) {
        if (graph.entry == null || graph.emit == null) return null;
        Map<String, NodeRuntime> runtimes = new HashMap<>();
        Map<String, Integer> visits = new HashMap<>();
        GraphNode cur = graph.entry;
        Candidate cand = null;
        boolean wrapPending = false;
        for (GraphNode n : graph.nodes) {
            if ("wrapIls".equals(n.type.id)) wrapPending = true;
        }
        long wrapReserve = 0L;
        long overallAtStart = ctx.overallDeadline();
        if (wrapPending && overallAtStart > 0) {
            long total = overallAtStart - System.nanoTime();
            wrapReserve = Math.min(WRAP_RESERVE_MAX_NANOS, total / 4);
            if (wrapReserve < 0) wrapReserve = 0;
        }
        while (true) {
            if (ctx.cancel.get()) return null;
            long overall = ctx.overallDeadline();
            if (overall > 0 && System.nanoTime() >= overall) return cand;
            if (cur == null || cur.type.emitMarker) return cand;
            if (cur.type.entryMarker) {
                cur = next(graph, cur, Guarantee.DONE);
                continue;
            }
            Integer seen = visits.get(cur.id);
            int v = seen == null ? 1 : seen + 1;
            visits.put(cur.id, v);
            if (v > MAX_NODE_VISITS) return cand;
            NodeRuntime rt = runtimes.get(cur.id);
            if (rt == null) {
                rt = cur.type.factory.create(cur.params);
                runtimes.put(cur.id, rt);
            }
            boolean isWrap = "wrapIls".equals(cur.type.id);
            if (isWrap) wrapPending = false;
            long budgetNanos = budgetNanos(cur);
            long deadline = budgetNanos > 0 ? System.nanoTime() + budgetNanos : 0L;
            if (overall > 0 && (deadline == 0L || overall < deadline)) deadline = overall;
            if (wrapPending && wrapReserve > 0 && overall > 0 && !isWrap) {
                long reserved = overall - wrapReserve;
                long now = System.nanoTime();
                if (reserved < now) reserved = now;
                if (deadline == 0L || reserved < deadline) deadline = reserved;
            }
            AtomicBoolean token = ctx.beginNode(cur, deadline, budgetNanos);
            Guarantee taken = null;
            try {
                NodeOutcome outcome = rt.execute(ctx, cand, token, deadline);
                taken = outcome.branch;
                cand = outcome.candidate;
            } catch (RuntimeException e) {
                if (ctx.cancel.get() || !token.get()) throw e;
                e.printStackTrace();
                taken = cur.type.fallbackBranch != null ? cur.type.fallbackBranch : Guarantee.NONE;
            } finally {
                ctx.endNode(cur, taken);
            }
            cur = next(graph, cur, taken);
        }
    }

    private static long budgetNanos(GraphNode n) {
        String p = n.type.budgetParam;
        if (p == null) return 0L;
        int secs = n.params.getInt(p);
        return secs > 0 ? secs * 1_000_000_000L : 0L;
    }

    private static GraphNode next(SolverGraph g, GraphNode n, Guarantee branch) {
        GraphEdge e = branch == null ? null : g.edgeFor(n.id, branch);
        return e == null ? g.emit : g.node(e.toNode);
    }
}
