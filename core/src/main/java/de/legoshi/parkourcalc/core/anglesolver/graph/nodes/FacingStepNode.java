package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FoldReplayDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.McSineTable;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.anglesolver.solver.YawTies;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FacingStepNode implements NodeRuntime {

    private static final double SAMPLE_STEP_DEG = 0.001;
    private static final int SEED_MS = 300;
    private static final int SEED_OBJECTIVE_ROUNDS = 32;

    private final int budgetSec;
    private final double windowDeg;
    private final int maxBuckets;
    private final int topK;

    private State state;

    public FacingStepNode(ParamValues params) {
        this.budgetSec = params.getInt("budgetSec");
        this.windowDeg = params.getDouble("windowDeg");
        this.maxBuckets = params.getInt("maxBuckets");
        this.topK = params.getInt("topK");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        Guarantee miss = in != null && in.yaws != null ? Guarantee.UNCHANGED : Guarantee.NONE;
        if (state == null || state.ctx != ctx) state = new State(ctx);
        if (state.disabled) return NodeOutcome.of(miss, in);
        if (!ctx.exact() || budgetSec <= 0) {
            state.disabled = true;
            return NodeOutcome.of(miss, in);
        }

        JumpPhysicsInputs sc = ctx.scenario;
        int n = sc.numTicks;

        if (!state.seeded) {
            state.seeded = true;
            int lead = leadingChain(ctx.spec.constraints, n);
            if (lead < 2 || lead >= n) {
                state.disabled = true;
                return NodeOutcome.of(miss, in);
            }
            state.max = ctx.maximize();
            state.originalStart = new Vec3dCore(sc.startPos.x, sc.startPos.y, sc.startPos.z);
            boolean incumbentFeasible = in != null && in.yaws != null && in.feasible;
            state.incumbentFeasible = incumbentFeasible;
            state.incumbentYaws = incumbentFeasible ? in.yaws.clone() : null;
            state.incumbentObj = incumbentFeasible ? ctx.exactObjective(in.yaws) : Double.NaN;
            double center = in != null && in.yaws != null ? in.yaws[0] : sc.startYaw;
            seed(ctx, lead, center, nodeToken, deadlineNanos);
            recordIncoming(ctx, in);
        } else {
            recordIncoming(ctx, in);
        }

        if (state.workIdx < state.work.size() && System.nanoTime() < deadlineNanos
                && !(nodeToken != null && nodeToken.get())) {
            Work w = state.work.get(state.workIdx++);
            if (ctx.freeStart) Scoring.adoptPinnedStart(sc, w.startX, w.startZ);
            return NodeOutcome.of(Guarantee.TRUE, Candidate.of(ctx, w.yaws));
        }

        return finish(ctx, in, miss);
    }

    private void recordIncoming(GraphContext ctx, Candidate in) {
        if (in == null || in.yaws == null) return;
        if (ctx.violationOf(in.yaws) > ctx.feasTol) return;
        JumpPhysicsInputs sc = ctx.scenario;
        if (ctx.freeStart) {
            double sx = sc.startPos.x;
            double sz = sc.startPos.z;
            if (sx < ctx.freeBox.pxLo - ctx.feasTol || sx > ctx.freeBox.pxHi + ctx.feasTol
                    || sz < ctx.freeBox.pzLo - ctx.feasTol || sz > ctx.freeBox.pzHi + ctx.feasTol) return;
        }
        double obj = ctx.exactObjective(in.yaws);
        state.best.consider(state.max, in.yaws.clone(), sc.startPos.x, sc.startPos.z, obj);
    }

    private NodeOutcome finish(GraphContext ctx, Candidate in, Guarantee miss) {
        JumpPhysicsInputs sc = ctx.scenario;
        if (SolverTrace.on()) {
            SolverTrace.log("ENGINE", "facing sweep buckets=%d best=%s",
                    state.work.size(), state.best.yaws == null ? "miss" : String.format("%.9f", state.best.obj));
        }
        boolean beats;
        if (!state.incumbentFeasible) {
            beats = state.best.yaws != null;
        } else {
            beats = state.best.yaws != null
                    && (state.max ? state.best.obj > state.incumbentObj : state.best.obj < state.incumbentObj);
        }
        if (beats) {
            if (ctx.freeStart) Scoring.adoptPinnedStart(sc, state.best.startX, state.best.startZ);
            ctx.chainAppend("facing sweep");
            return NodeOutcome.of(Guarantee.IMPROVED, Candidate.of(ctx, state.best.yaws));
        }
        if (ctx.freeStart) Scoring.adoptPinnedStart(sc, state.originalStart.x, state.originalStart.z);
        if (state.incumbentFeasible && state.incumbentYaws != null) {
            return NodeOutcome.of(Guarantee.UNCHANGED, Candidate.of(ctx, state.incumbentYaws));
        }
        return NodeOutcome.of(miss, in);
    }

    private void seed(GraphContext ctx, int lead, double center, AtomicBoolean cancel, long deadlineNanos) {
        JumpPhysicsInputs sc = ctx.scenario;
        boolean locked0 = sc.yawLockedPerTick != null && sc.yawLockedPerTick.length > 0 && sc.yawLockedPerTick[0];
        List<Double> bucketYaws = enumerate(sc.startYaw, locked0, center, windowDeg, maxBuckets);
        List<Seed> seeds = new ArrayList<>();
        for (Double y0 : bucketYaws) {
            if (cancel != null && cancel.get()) break;
            if (deadlineNanos > 0 && System.nanoTime() >= deadlineNanos) break;
            double yaw0 = y0;
            Bucket bd = prep(ctx, yaw0, lead);
            if (bd == null) continue;
            long seedDeadline = System.nanoTime() + SEED_MS * 1_000_000L;
            if (deadlineNanos > 0) seedDeadline = Math.min(seedDeadline, deadlineNanos);
            double[] tail = solveSeed(ctx, bd, seedDeadline, cancel);
            if (tail == null) continue;
            double obj = verifyFull(ctx, bd, yaw0, lead, tail, bd.px, bd.pz);
            if (Double.isNaN(obj)) continue;
            double[] full = fullYaws(yaw0, lead, tail, sc.numTicks);
            Vec3dCore start = startFull(ctx, bd, bd.px, bd.pz);
            seeds.add(new Seed(full, start.x, start.z, windowObjective(ctx, bd, bd.px, bd.pz, tail)));
        }
        seeds.sort((a, b) -> state.max ? Double.compare(b.rank, a.rank) : Double.compare(a.rank, b.rank));
        int kept = Math.min(topK, seeds.size());
        for (int i = 0; i < kept; i++) {
            Seed s = seeds.get(i);
            state.work.add(new Work(s.yaws, s.startX, s.startZ));
        }
    }

    private double windowObjective(GraphContext ctx, Bucket bd, double px, double pz, double[] tail) {
        JumpSpec win = windowSpec(ctx, bd, px, pz);
        return Scoring.verifiedObjectiveAt(ctx.model, win.asScenario(), win, tail, px, pz, ctx.feasTol);
    }

    private double[] solveSeed(GraphContext ctx, Bucket bd, long deadline, AtomicBoolean cancel) {
        ExactJumpModel em = ctx.exactModel;
        JumpSpec win = windowSpec(ctx, bd, bd.px, bd.pz);
        double[] cf = ClosedFormSolve.optimize(em, win, ctx.feasTol, cancel);
        double bestObj = Double.NaN;
        double[] bestTail = null;
        boolean max = ctx.maximize();
        if (cf != null) {
            double o = Scoring.verifiedObjectiveAt(ctx.model, win.asScenario(), win, cf, bd.px, bd.pz, ctx.feasTol);
            if (!Double.isNaN(o)) {
                bestObj = o;
                bestTail = cf;
            }
        }
        FoldReplayDriver.Params p = new FoldReplayDriver.Params();
        p.cancel = cancel;
        p.deadlineNanos = deadline;
        p.objectiveRounds = SEED_OBJECTIVE_ROUNDS;
        FoldReplayDriver.Result res = FoldReplayDriver.solve(em, win, p);
        if (res.best != null && res.best.feasible()) {
            double o = Scoring.verifiedObjectiveAt(ctx.model, win.asScenario(), win, res.best.yawsDeg,
                    bd.px, bd.pz, ctx.feasTol);
            if (!Double.isNaN(o) && (bestTail == null || (max ? o > bestObj : o < bestObj))) {
                bestObj = o;
                bestTail = res.best.yawsDeg;
            }
        }
        return bestTail;
    }

    private JumpSpec windowSpec(GraphContext ctx, Bucket bd, double px, double pz) {
        JumpSpec win = LongRunSolver.suffixSpec(ctx.spec, bd.lead,
                new Vec3dCore(px, bd.ty, pz), bd.vel, bd.takeoffYaw);
        win.asScenario().startBox = StartBox.pinned(px, pz, bd.vel.x, bd.vel.z);
        return win;
    }

    private Bucket prep(GraphContext ctx, double yaw0, int lead) {
        JumpPhysicsInputs sc = ctx.scenario;
        int n = sc.numTicks;
        double[] yawsFull = new double[n];
        Arrays.fill(yawsFull, yaw0);
        double[] gf = sc.toGameFacings(yawsFull);
        ForwardPath path = ctx.exactModel.forward(sc, gf);
        double tx = path.posX[lead];
        double tz = path.posZ[lead];
        double ty = path.posY[lead];
        Vec3dCore vel = new Vec3dCore(path.velX[lead], path.velY[lead], path.velZ[lead]);
        float takeoffYaw = (float) gf[lead - 1];

        double xLo = Double.NEGATIVE_INFINITY, xHi = Double.POSITIVE_INFINITY;
        double zLo = Double.NEGATIVE_INFINITY, zHi = Double.POSITIVE_INFINITY;
        for (JumpConstraint c : ctx.spec.constraints) {
            if (c.t2 != null) continue;
            if (c.t1 < 0 || c.t1 > lead) continue;
            if (c.mode == JumpConstraint.Mode.X) {
                double d = path.posX[c.t1] - tx;
                if (c.cmp != JumpConstraint.Cmp.LE) xLo = Math.max(xLo, c.rhs - d);
                if (c.cmp != JumpConstraint.Cmp.GE) xHi = Math.min(xHi, c.rhs - d);
            } else if (c.mode == JumpConstraint.Mode.Z) {
                double d = path.posZ[c.t1] - tz;
                if (c.cmp != JumpConstraint.Cmp.LE) zLo = Math.max(zLo, c.rhs - d);
                if (c.cmp != JumpConstraint.Cmp.GE) zHi = Math.min(zHi, c.rhs - d);
            }
        }
        if (ctx.freeStart) {
            double dx = tx - path.posX[0];
            double dz = tz - path.posZ[0];
            xLo = Math.max(xLo, ctx.freeBox.pxLo + dx);
            xHi = Math.min(xHi, ctx.freeBox.pxHi + dx);
            zLo = Math.max(zLo, ctx.freeBox.pzLo + dz);
            zHi = Math.min(zHi, ctx.freeBox.pzHi + dz);
        }
        if (xLo > xHi || zLo > zHi) return null;

        double px, pz;
        if (ctx.freeStart) {
            px = clamp(tx, xLo, xHi);
            pz = clamp(tz, zLo, zHi);
        } else {
            if (tx < xLo - ctx.feasTol || tx > xHi + ctx.feasTol
                    || tz < zLo - ctx.feasTol || tz > zHi + ctx.feasTol) return null;
            px = tx;
            pz = tz;
        }
        return new Bucket(lead, yaw0, tx, tz, ty, px, pz, vel, takeoffYaw, xLo, xHi, zLo, zHi);
    }

    private double verifyFull(GraphContext ctx, Bucket bd, double yaw0, int lead, double[] tail, double px, double pz) {
        if (tail == null) return Double.NaN;
        double[] full = fullYaws(yaw0, lead, tail, ctx.scenario.numTicks);
        Vec3dCore start = startFull(ctx, bd, px, pz);
        if (ctx.freeStart && (start.x < ctx.freeBox.pxLo - ctx.feasTol || start.x > ctx.freeBox.pxHi + ctx.feasTol
                || start.z < ctx.freeBox.pzLo - ctx.feasTol || start.z > ctx.freeBox.pzHi + ctx.feasTol)) {
            return Double.NaN;
        }
        return Scoring.verifiedObjectiveAt(ctx.model, ctx.scenario, ctx.spec, full, start.x, start.z, ctx.feasTol);
    }

    private Vec3dCore startFull(GraphContext ctx, Bucket bd, double px, double pz) {
        JumpPhysicsInputs sc = ctx.scenario;
        return new Vec3dCore(state.originalStart.x + (px - bd.tx0), sc.startPos.y, state.originalStart.z + (pz - bd.tz0));
    }

    private static double[] fullYaws(double yaw0, int lead, double[] tail, int n) {
        double[] full = new double[n];
        for (int t = 0; t < lead; t++) full[t] = yaw0;
        for (int t = lead; t < n; t++) full[t] = tail[t - lead];
        return Angles.wrapAll(full);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static int leadingChain(List<JumpConstraint> constraints, int n) {
        YawTies ties = YawTies.of(constraints, n);
        if (ties == null) return -1;
        int g0 = ties.groupOf(0);
        int lead = 1;
        while (lead < n && ties.groupOf(lead) == g0) lead++;
        return lead;
    }

    public static int sineBucket(float gameFacing) {
        float rad = gameFacing * (float) Math.PI / 180.0F;
        return (int) (rad * McSineTable.INDEX_FROM_RAD) & McSineTable.MASK;
    }

    public static float gf0(float startYaw, boolean locked0, double yaw0) {
        if (locked0) return (float) yaw0;
        return startYaw + (float) Angles.wrapDelta(yaw0 - (double) startYaw);
    }

    public static List<Double> enumerate(float startYaw, boolean locked0, double center, double windowDeg, int maxBuckets) {
        List<Double> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        out.add(center);
        seen.add(sineBucket(gf0(startYaw, locked0, center)));
        if (windowDeg > 0.0 && maxBuckets > 1) {
            int k = 1;
            int steps = (int) Math.ceil(windowDeg / SAMPLE_STEP_DEG);
            while (k <= steps && out.size() < maxBuckets) {
                for (int s = -1; s <= 1; s += 2) {
                    double y = center + s * k * SAMPLE_STEP_DEG;
                    if (Math.abs(y - center) > windowDeg + 1.0e-12) continue;
                    int b = sineBucket(gf0(startYaw, locked0, y));
                    if (seen.add(b)) {
                        out.add(y);
                        if (out.size() >= maxBuckets) break;
                    }
                }
                k++;
            }
        }
        return out;
    }

    private static final class State {
        final GraphContext ctx;
        boolean seeded;
        boolean disabled;
        boolean max;
        Vec3dCore originalStart;
        boolean incumbentFeasible;
        double[] incumbentYaws;
        double incumbentObj = Double.NaN;
        final List<Work> work = new ArrayList<>();
        int workIdx;
        final Best best = new Best();

        State(GraphContext ctx) {
            this.ctx = ctx;
        }
    }

    private static final class Work {
        final double[] yaws;
        final double startX;
        final double startZ;

        Work(double[] yaws, double startX, double startZ) {
            this.yaws = yaws;
            this.startX = startX;
            this.startZ = startZ;
        }
    }

    private static final class Seed {
        final double[] yaws;
        final double startX;
        final double startZ;
        final double rank;

        Seed(double[] yaws, double startX, double startZ, double rank) {
            this.yaws = yaws;
            this.startX = startX;
            this.startZ = startZ;
            this.rank = rank;
        }
    }

    private static final class Bucket {
        final int lead;
        final double yaw0;
        final double tx0;
        final double tz0;
        final double ty;
        final double px;
        final double pz;
        final Vec3dCore vel;
        final float takeoffYaw;
        final double xLo;
        final double xHi;
        final double zLo;
        final double zHi;

        Bucket(int lead, double yaw0, double tx0, double tz0, double ty, double px, double pz, Vec3dCore vel,
               float takeoffYaw, double xLo, double xHi, double zLo, double zHi) {
            this.lead = lead;
            this.yaw0 = yaw0;
            this.tx0 = tx0;
            this.tz0 = tz0;
            this.ty = ty;
            this.px = px;
            this.pz = pz;
            this.vel = vel;
            this.takeoffYaw = takeoffYaw;
            this.xLo = xLo;
            this.xHi = xHi;
            this.zLo = zLo;
            this.zHi = zHi;
        }
    }

    private static final class Best {
        double[] yaws;
        double startX;
        double startZ;
        double obj = Double.NaN;

        void consider(boolean max, double[] candYaws, double x, double z, double candObj) {
            if (Double.isNaN(candObj)) return;
            if (yaws == null || (max ? candObj > obj : candObj < obj)) {
                yaws = candYaws;
                startX = x;
                startZ = z;
                obj = candObj;
            }
        }
    }
}
