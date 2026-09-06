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
            wrapReserve = wrapReserveNanos(overallAtStart - System.nanoTime());
        }
        while (true) {
            if (ctx.cancel.get()) return null;
            long overall = ctx.overallDeadline();
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
            if (isWrap || (wrapPending && !reachesWrap(graph, cur))) wrapPending = false;
            long budgetNanos = budgetNanos(cur);
            long now = System.nanoTime();
            long deadline = budgetNanos > 0 ? now + budgetNanos : 0L;
            if (overall > 0 && (deadline == 0L || overall < deadline)) deadline = overall;
            if (overall > 0 && now >= overall) deadline = now;
            if (wrapPending && wrapReserve > 0 && overall > 0 && !isWrap) {
                long reserved = overall - wrapReserve;
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
            reportIncumbent(ctx, cand);
            cur = next(graph, cur, taken);
        }
    }

    public static long wrapReserveNanos(long totalNanos) {
        long reserve = Math.min(WRAP_RESERVE_MAX_NANOS, totalNanos / 4);
        return reserve > 0 ? reserve : 0L;
    }

    private static void reportIncumbent(GraphContext ctx, Candidate cand) {
        if (ctx.progress == null || cand == null || cand.yaws == null) return;
        double violation = ctx.violationOf(cand.yaws);
        ctx.progress.setStage(ctx.chain());
        ctx.progress.report(cand.yaws, ctx.exactObjective(cand.yaws), violation, violation <= ctx.feasTol);
    }

    private static long budgetNanos(GraphNode n) {
        long ms = budgetMillis(n);
        if (ms > 0) return ms * 1_000_000L;
        String p = n.type.budgetParam;
        if (p == null) return 0L;
        int secs = n.params.getInt(p);
        return secs > 0 ? secs * 1_000_000_000L : 0L;
    }

    private static long budgetMillis(GraphNode n) {
        for (ParamSpec s : n.type.params) {
            if ("budgetMs".equals(s.key)) return n.params.getInt("budgetMs");
        }
        return 0L;
    }

    private static boolean reachesWrap(SolverGraph g, GraphNode from) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<GraphNode> queue = new java.util.ArrayDeque<>();
        queue.add(from);
        seen.add(from.id);
        while (!queue.isEmpty()) {
            GraphNode n = queue.poll();
            if ("wrapIls".equals(n.type.id)) return true;
            for (GraphEdge e : g.edges) {
                if (!e.fromNode.equals(n.id)) continue;
                GraphNode to = g.node(e.toNode);
                if (to != null && seen.add(to.id)) queue.add(to);
            }
        }
        return false;
    }

    private static GraphNode next(SolverGraph g, GraphNode n, Guarantee branch) {
        GraphEdge e = branch == null ? null : g.edgeFor(n.id, branch);
        return e == null ? g.emit : g.node(e.toNode);
    }
}
