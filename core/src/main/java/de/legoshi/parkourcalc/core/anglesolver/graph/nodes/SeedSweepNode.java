package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunner;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SeedSweepNode implements NodeRuntime {

    private final int seeds;
    private final int threads;

    public SeedSweepNode(ParamValues params) {
        this.seeds = params.getInt("seeds");
        this.threads = params.getInt("threads");
    }

    private static final class SeedResult {
        final double[] yaws;
        final double px;
        final double pz;
        final double objective;

        SeedResult(double[] yaws, double px, double pz, double objective) {
            this.yaws = yaws;
            this.px = px;
            this.pz = pz;
            this.objective = objective;
        }
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact() || !ctx.freeStart || ctx.freeBox == null || ctx.stageLocked() || seeds <= 0) {
            return NodeOutcome.of(Guarantee.NONE, in);
        }
        List<double[]> refs = seedPoints(ctx.freeBox, seeds);
        SeedResult[] out = new SeedResult[refs.size()];
        int nThreads = threadCount(ctx, refs.size());
        SolverGraph fast = BuiltinGraphs.fast();
        long t0 = System.nanoTime();
        if (nThreads <= 1) {
            for (int i = 0; i < refs.size(); i++) {
                if (nodeToken != null && nodeToken.get()) break;
                out[i] = runSeed(ctx, fast, refs.get(i), nodeToken);
            }
        } else {
            runParallel(ctx, fast, refs, out, nThreads, nodeToken);
        }
        SeedResult best = null;
        int feasibleCount = 0;
        boolean max = ctx.maximize();
        for (SeedResult r : out) {
            if (r == null) continue;
            feasibleCount++;
            if (best == null || (max ? r.objective > best.objective : r.objective < best.objective)) best = r;
        }
        if (SolverTrace.on()) {
            SolverTrace.log("SWEEP", "seeds=%d feasible=%d threads=%d ms=%d best=%s", refs.size(), feasibleCount,
                    nThreads, (System.nanoTime() - t0) / 1_000_000L,
                    best == null ? "none" : String.format(java.util.Locale.ROOT, "%.9f@(%.5f,%.5f)",
                            best.objective, best.px, best.pz));
        }
        if (best == null) return NodeOutcome.of(Guarantee.NONE, in);
        if (in != null && in.yaws != null && in.feasible) {
            double cur = ctx.exactObjective(in.yaws);
            if (!(max ? best.objective > cur : best.objective < cur)) return NodeOutcome.of(Guarantee.FOUND, in);
        }
        Scoring.adoptPinnedStart(ctx.scenario, best.px, best.pz);
        ctx.chainAppend("seed sweep " + feasibleCount + "/" + refs.size());
        return NodeOutcome.of(Guarantee.FOUND, Candidate.of(ctx, best.yaws));
    }

    private int threadCount(GraphContext ctx, int work) {
        if (ctx.sequential) return 1;
        int n = threads > 0 ? threads : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        return Math.max(1, Math.min(n, work));
    }

    private void runParallel(GraphContext ctx, SolverGraph fast, List<double[]> refs, SeedResult[] out,
                             int nThreads, AtomicBoolean nodeToken) {
        AtomicInteger seq = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(nThreads, r -> {
            Thread t = new Thread(r, "seed-sweep-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < refs.size(); i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                if (nodeToken != null && nodeToken.get()) return;
                out[idx] = runSeed(ctx, fast, refs.get(idx), nodeToken);
            }));
        }
        pool.shutdown();
        try {
            for (Future<?> f : futures) f.get();
        } catch (Exception e) {
            pool.shutdownNow();
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    private static SeedResult runSeed(GraphContext ctx, SolverGraph fast, double[] ref, AtomicBoolean nodeToken) {
        JumpPhysicsInputs sc = ctx.scenario.copy();
        sc.startPos = new Vec3dCore(ref[0], sc.startPos.y, ref[1]);
        sc.startBox = StartBox.pinned(ref[0], ref[1], sc.initialVelocity.x, sc.initialVelocity.z);
        JumpSpec spec = new JumpSpec(sc, new ArrayList<>(ctx.spec.constraints), ctx.spec.objective);
        AtomicBoolean cancel = nodeToken != null ? nodeToken : new AtomicBoolean(false);
        GraphContext sub = new GraphContext(spec, ctx.model, ctx.freeBox, ctx.legalGoal, ctx.feasTol, cancel,
                null, true, ctx.longRun);
        Candidate c;
        try {
            c = GraphRunner.run(fast, sub);
        } catch (RuntimeException e) {
            if (SolverTrace.on()) SolverTrace.log("SWEEP", "seed (%.5f,%.5f) failed: %s", ref[0], ref[1], e);
            return null;
        }
        if (c == null || c.yaws == null || !c.feasible) return null;
        double px = sub.scenario.startPos.x;
        double pz = sub.scenario.startPos.z;
        double obj = Scoring.verifiedObjectiveAt(ctx.model, ctx.scenario, ctx.spec, c.yaws, px, pz, ctx.feasTol);
        if (Double.isNaN(obj)) return null;
        return new SeedResult(c.yaws, px, pz, obj);
    }

    static List<double[]> seedPoints(StartBox box, int count) {
        List<double[]> refs = new ArrayList<>();
        boolean freeX = box.pxHi > box.pxLo;
        boolean freeZ = box.pzHi > box.pzLo;
        double[][] anchors = {
                {0.5, 0.5}, {0.0, 0.0}, {1.0, 1.0}, {0.0, 1.0}, {1.0, 0.0}};
        for (double[] a : anchors) {
            if (refs.size() >= count) break;
            addUnique(refs, box, freeX ? a[0] : 0.0, freeZ ? a[1] : 0.0);
        }
        int i = 1;
        while (refs.size() < count && i < count * 8) {
            addUnique(refs, box, freeX ? halton(i, 2) : 0.0, freeZ ? halton(i, 3) : 0.0);
            i++;
        }
        return refs;
    }

    private static void addUnique(List<double[]> refs, StartBox box, double fx, double fz) {
        double px = box.pxLo + fx * (box.pxHi - box.pxLo);
        double pz = box.pzLo + fz * (box.pzHi - box.pzLo);
        for (double[] r : refs) {
            if (r[0] == px && r[1] == pz) return;
        }
        refs.add(new double[]{px, pz});
    }

    static double halton(int index, int base) {
        double f = 1.0;
        double r = 0.0;
        int i = index;
        while (i > 0) {
            f /= base;
            r += f * (i % base);
            i /= base;
        }
        return r;
    }
}
