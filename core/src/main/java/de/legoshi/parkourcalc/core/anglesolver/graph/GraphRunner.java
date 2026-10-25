package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GraphRunner {

    public static final int MAX_NODE_VISITS = 1024;
    public static final String RESERVE_FRACTION_PARAM = "reserveFraction";
    public static final String RESERVE_CAP_PARAM = "reserveCapSec";

    private GraphRunner() {
    }

    public static Candidate run(SolverGraph graph, GraphContext ctx) {
        return run(graph, ctx, null);
    }

    public static Candidate run(SolverGraph graph, GraphContext ctx, Candidate initial) {
        try {
            return walk(graph, ctx, initial);
        } finally {
            ctx.shutdown();
        }
    }

    private static Candidate walk(SolverGraph graph, GraphContext ctx, Candidate initial) {
        if (graph.entry == null || graph.emit == null) return null;
        Map<String, NodeRuntime> runtimes = new HashMap<>();
        Map<String, Integer> visits = new HashMap<>();
        GraphNode cur = graph.entry;
        Candidate cand = initial;
        long overallAtStart = ctx.overallDeadline();
        long totalNanos = overallAtStart > 0 ? overallAtStart - System.nanoTime() : 0L;
        Map<String, Long> reserves = new HashMap<>();
        for (GraphNode n : graph.nodes) {
            long r = reserveNanos(n, totalNanos);
            if (r > 0) reserves.put(n.id, r);
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
            long budgetNanos = budgetNanos(cur, totalNanos);
            long now = System.nanoTime();
            long deadline = budgetNanos > 0 ? now + budgetNanos : 0L;
            if (overall > 0 && (deadline == 0L || overall < deadline)) deadline = overall;
            if (overall > 0 && now >= overall) deadline = now;
            long pendingReserve = overall > 0 && !reserves.isEmpty() ? pendingReserve(graph, cur, reserves, visits) : 0L;
            if (pendingReserve > 0) {
                long reserved = overall - pendingReserve;
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
            if (TRACE) traceNode(ctx, cur, taken, cand);
            reportIncumbent(ctx, cand);
            if (ctx.infeasibilityProof() != null) return cand;
            cur = next(graph, cur, taken);
        }
    }

    public static final boolean TRACE = Boolean.getBoolean("pkc.graphTrace");

    private static void traceNode(GraphContext ctx, GraphNode node, Guarantee taken, Candidate cand) {
        NodeStatus st = ctx.runState.status(node.id);
        double ms = st == null ? -1.0 : st.elapsedNanos / 1e6;
        String v = cand == null || cand.yaws == null ? "-" : String.format(java.util.Locale.ROOT, "%.3g", ctx.violationOf(cand.yaws));
        System.out.println(String.format(java.util.Locale.ROOT, "[graph] %-12s %-9s %8.1f ms viol=%s", node.id, taken, ms, v));
    }

    public static boolean reserving(NodeType type) {
        for (ParamSpec p : type.params) {
            if (RESERVE_FRACTION_PARAM.equals(p.key)) return true;
        }
        return false;
    }

    public static long reserveNanos(GraphNode n, long totalNanos) {
        if (totalNanos <= 0 || !reserving(n.type)) return 0L;
        return reserveNanos(n.params.getDouble(RESERVE_FRACTION_PARAM), n.params.getInt(RESERVE_CAP_PARAM), totalNanos);
    }

    public static long reserveNanos(double fraction, int capSec, long totalNanos) {
        if (totalNanos <= 0 || fraction <= 0.0) return 0L;
        long reserve = (long) (totalNanos * fraction);
        if (capSec > 0) reserve = Math.min(reserve, capSec * 1_000_000_000L);
        return reserve > 0 ? reserve : 0L;
    }

    private static void reportIncumbent(GraphContext ctx, Candidate cand) {
        if (ctx.progress == null || cand == null || cand.yaws == null) return;
        double violation = ctx.violationOf(cand.yaws);
        ctx.progress.setStage(ctx.chain());
        ctx.progress.report(cand.yaws, ctx.exactObjective(cand.yaws), violation, violation <= ctx.feasTol);
    }

    private static long budgetNanos(GraphNode n, long totalNanos) {
        long ms = budgetMillis(n);
        if (ms > 0) return ms * 1_000_000L;
        String p = n.type.budgetParam;
        if (p != null) {
            int secs = n.params.getInt(p);
            if (secs > 0) return secs * 1_000_000_000L;
        }
        return reserveNanos(n, totalNanos);
    }

    private static long budgetMillis(GraphNode n) {
        for (ParamSpec s : n.type.params) {
            if ("budgetMs".equals(s.key)) return n.params.getInt("budgetMs");
        }
        return 0L;
    }

    private static long pendingReserve(SolverGraph g, GraphNode from, Map<String, Long> reserves,
                                       Map<String, Integer> visits) {
        long sum = 0L;
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<GraphNode> queue = new java.util.ArrayDeque<>();
        queue.add(from);
        seen.add(from.id);
        while (!queue.isEmpty()) {
            GraphNode n = queue.poll();
            if (n != from && !visits.containsKey(n.id)) {
                Long r = reserves.get(n.id);
                if (r != null) sum += r;
            }
            for (GraphEdge e : g.edges) {
                if (!e.fromNode.equals(n.id)) continue;
                GraphNode to = g.node(e.toNode);
                if (to != null && seen.add(to.id)) queue.add(to);
            }
        }
        return sum;
    }

    private static GraphNode next(SolverGraph g, GraphNode n, Guarantee branch) {
        GraphEdge e = branch == null ? null : g.edgeFor(n.id, branch);
        return e == null ? g.emit : g.node(e.toNode);
    }
}
