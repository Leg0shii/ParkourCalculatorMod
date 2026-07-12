package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GraphRunner {

    public static final int MAX_NODE_VISITS = 1024;

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
        while (true) {
            if (ctx.cancel.get()) return null;
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
            long budgetNanos = budgetNanos(cur);
            long deadline = budgetNanos > 0 ? System.nanoTime() + budgetNanos : 0L;
            AtomicBoolean token = ctx.beginNode(cur, deadline, budgetNanos);
            Guarantee taken = null;
            try {
                NodeOutcome outcome = rt.execute(ctx, cand, token, deadline);
                taken = outcome.branch;
                cand = outcome.candidate;
            } catch (RuntimeException e) {
                if (ctx.cancel.get() || !token.get()) throw e;
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
