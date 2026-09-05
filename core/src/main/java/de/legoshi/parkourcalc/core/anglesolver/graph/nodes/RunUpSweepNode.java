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
import de.legoshi.parkourcalc.core.anglesolver.solver.IlsPolish;
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

public final class RunUpSweepNode implements NodeRuntime {

    private static final double SAMPLE_STEP_DEG = 0.001;
    private static final int STAGE1_OBJECTIVE_ROUNDS = 32;
    private static final int STAGE2_OBJECTIVE_ROUNDS = 64;

    private final int budgetSec;
    private final double windowDeg;
    private final int maxBuckets;
    private final int stage1Ms;
    private final int topK;
    private final int stage2Sec;
    private final String positionMode;

    public RunUpSweepNode(ParamValues params) {
        this.budgetSec = params.getInt("budgetSec");
        this.windowDeg = params.getDouble("windowDeg");
        this.maxBuckets = params.getInt("maxBuckets");
        this.stage1Ms = params.getInt("stage1Ms");
        this.topK = params.getInt("topK");
        this.stage2Sec = params.getInt("stage2Sec");
        this.positionMode = params.getString("positionMode");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        Guarantee miss = in != null && in.yaws != null ? Guarantee.UNCHANGED : Guarantee.NONE;
        if (!ctx.exact()) return NodeOutcome.of(miss, in);

        JumpSpec spec = ctx.spec;
        JumpPhysicsInputs sc = ctx.scenario;
        int n = sc.numTicks;
        int lead = leadingChain(spec.constraints, n);
        if (lead < 2 || lead >= n) return NodeOutcome.of(miss, in);

        boolean haveIncumbent = in != null && in.yaws != null;
        boolean incumbentFeasible = haveIncumbent && in.feasible;
        double incumbentObj = incumbentFeasible ? ctx.exactObjective(in.yaws) : Double.NaN;
        double center = haveIncumbent ? in.yaws[0] : sc.startYaw;

        long nodeDeadline = System.nanoTime() + budgetSec * 1_000_000_000L;
        if (deadlineNanos > 0) nodeDeadline = Math.min(nodeDeadline, deadlineNanos);

        boolean locked0 = sc.yawLockedPerTick != null && sc.yawLockedPerTick.length > 0 && sc.yawLockedPerTick[0];
        List<Double> bucketYaws = enumerate(sc.startYaw, locked0, center, windowDeg, maxBuckets);

        boolean max = ctx.maximize();
        boolean doPin = !"free".equals(positionMode);
        boolean doFree = ctx.freeStart && !"pin".equals(positionMode);

        Best best = new Best(max);
        List<Bucket> feasible = new ArrayList<>();
        int enumerated = 0;
        int feasibleCount = 0;

        for (Double y0 : bucketYaws) {
            if (nodeToken != null && nodeToken.get()) break;
            if (System.nanoTime() >= nodeDeadline) break;
            double yaw0 = y0;
            Bucket bd = prep(ctx, yaw0, lead);
            if (bd == null) continue;
            enumerated++;

            long stage1Deadline = Math.min(nodeDeadline, System.nanoTime() + stage1Ms * 1_000_000L);
            double[] tail = solvePinned(ctx, bd, STAGE1_OBJECTIVE_ROUNDS, stage1Deadline, nodeToken);
            if (tail == null) continue;
            feasibleCount++;

            double obj = verifyFull(ctx, bd, yaw0, lead, tail, bd.px, bd.pz);
            if (!Double.isNaN(obj)) {
                bd.tail = tail;
                bd.stage1Obj = obj;
                feasible.add(bd);
                best.consider(fullYaws(yaw0, lead, tail, n), startFull(ctx, bd, bd.px, bd.pz), obj);
            }
        }

        feasible.sort((a, b) -> max ? Double.compare(b.stage1Obj, a.stage1Obj) : Double.compare(a.stage1Obj, b.stage1Obj));
        int kept = Math.min(topK, feasible.size());
        int polished = 0;

        for (int i = 0; i < kept; i++) {
            if (nodeToken != null && nodeToken.get()) break;
            if (System.nanoTime() >= nodeDeadline) break;
            Bucket bd = feasible.get(i);
            polished++;

            if (doPin) {
                long ilsDeadline = Math.min(nodeDeadline, System.nanoTime() + stage2Sec * 1_000_000_000L);
                JumpSpec win = windowSpec(ctx, bd, bd.px, bd.pz, null);
                double[] pol = IlsPolish.polish(ctx.model, win, bd.tail, ilsDeadline, Integer.MAX_VALUE,
                        ctx.sequential, nodeToken, null, ilsConfig());
                double obj = verifyFull(ctx, bd, bd.yaw0, lead, pol, bd.px, bd.pz);
                if (!Double.isNaN(obj)) best.consider(fullYaws(bd.yaw0, lead, pol, n), startFull(ctx, bd, bd.px, bd.pz), obj);
            }

            if (doFree) {
                if (nodeToken != null && nodeToken.get()) break;
                if (System.nanoTime() >= nodeDeadline) break;
                long freeDeadline = Math.min(nodeDeadline, System.nanoTime() + stage2Sec * 1_000_000_000L);
                StartBox box = new StartBox(bd.px, bd.pz, bd.vel.x, bd.vel.z,
                        bd.xLo, bd.xHi, bd.zLo, bd.zHi, bd.vel.x, bd.vel.x, bd.vel.z, bd.vel.z);
                JumpSpec win = windowSpec(ctx, bd, bd.px, bd.pz, box);
                FoldReplayDriver.Params p = new FoldReplayDriver.Params();
                p.cancel = nodeToken;
                p.deadlineNanos = freeDeadline;
                p.objectiveRounds = STAGE2_OBJECTIVE_ROUNDS;
                FoldReplayDriver.Result res = FoldReplayDriver.solve(ctx.exactModel, win, p);
                if (res.best != null && res.best.feasible()) {
                    double[] freeTail = res.best.yawsDeg;
                    double fx = res.best.px;
                    double fz = res.best.pz;
                    double obj = verifyFull(ctx, bd, bd.yaw0, lead, freeTail, fx, fz);
                    if (!Double.isNaN(obj)) best.consider(fullYaws(bd.yaw0, lead, freeTail, n), startFull(ctx, bd, fx, fz), obj);
                    long ilsDeadline = Math.min(nodeDeadline, System.nanoTime() + stage2Sec * 1_000_000_000L);
                    JumpSpec winPin = windowSpec(ctx, bd, fx, fz, null);
                    double[] pol = IlsPolish.polish(ctx.model, winPin, freeTail, ilsDeadline, Integer.MAX_VALUE,
                            ctx.sequential, nodeToken, null, ilsConfig());
                    double obj2 = verifyFull(ctx, bd, bd.yaw0, lead, pol, fx, fz);
                    if (!Double.isNaN(obj2)) best.consider(fullYaws(bd.yaw0, lead, pol, n), startFull(ctx, bd, fx, fz), obj2);
                }
            }
        }

        if (SolverTrace.on()) {
            SolverTrace.log("ENGINE", "run-up sweep buckets=%d feasible=%d polished=%d best=%s",
                    enumerated, feasibleCount, polished,
                    best.yaws == null ? "miss" : String.format("%.9f", best.obj));
        }

        if (best.yaws == null) return NodeOutcome.of(miss, in);
        boolean better = !incumbentFeasible || (max ? best.obj > incumbentObj : best.obj < incumbentObj);
        if (!better) return NodeOutcome.of(miss, in);

        if (ctx.freeStart && (best.startX != sc.startPos.x || best.startZ != sc.startPos.z)) {
            Scoring.adoptPinnedStart(sc, best.startX, best.startZ);
        }
        ctx.chainAppend("run-up sweep");
        return NodeOutcome.of(incumbentFeasible ? Guarantee.IMPROVED : Guarantee.FOUND, Candidate.of(ctx, best.yaws));
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

    private JumpSpec windowSpec(GraphContext ctx, Bucket bd, double px, double pz, StartBox box) {
        JumpSpec win = LongRunSolver.suffixSpec(ctx.spec, bd.lead,
                new Vec3dCore(px, bd.ty, pz), bd.vel, bd.takeoffYaw);
        win.asScenario().startBox = box != null ? box : StartBox.pinned(px, pz, bd.vel.x, bd.vel.z);
        return win;
    }

    private double[] solvePinned(GraphContext ctx, Bucket bd, int objectiveRounds, long deadline, AtomicBoolean cancel) {
        ExactJumpModel em = ctx.exactModel;
        JumpSpec win = windowSpec(ctx, bd, bd.px, bd.pz, null);
        double[] cf = ClosedFormSolve.optimize(em, win, ctx.feasTol, cancel);
        double bestObj = Double.NaN;
        double[] bestTail = null;
        boolean max = ctx.maximize();
        if (cf != null) {
            double o = windowObjective(ctx, win, bd.px, bd.pz, cf);
            if (!Double.isNaN(o)) {
                bestObj = o;
                bestTail = cf;
            }
        }
        FoldReplayDriver.Params p = new FoldReplayDriver.Params();
        p.cancel = cancel;
        p.deadlineNanos = deadline;
        p.objectiveRounds = objectiveRounds;
        FoldReplayDriver.Result res = FoldReplayDriver.solve(em, win, p);
        if (res.best != null && res.best.feasible()) {
            double o = windowObjective(ctx, win, bd.px, bd.pz, res.best.yawsDeg);
            if (!Double.isNaN(o) && (bestTail == null || (max ? o > bestObj : o < bestObj))) {
                bestObj = o;
                bestTail = res.best.yawsDeg;
            }
        }
        return bestTail;
    }

    private double windowObjective(GraphContext ctx, JumpSpec win, double px, double pz, double[] tail) {
        return Scoring.verifiedObjectiveAt(ctx.model, win.asScenario(), win, tail, px, pz, ctx.feasTol);
    }

    private double verifyFull(GraphContext ctx, Bucket bd, double yaw0, int lead, double[] tail, double px, double pz) {
        if (tail == null) return Double.NaN;
        double[] full = fullYaws(yaw0, lead, tail, ctx.scenario.numTicks);
        Vec3dCore start = startFull(ctx, bd, px, pz);
        if (ctx.freeStart && (start.x < ctx.freeBox.pxLo - ctx.feasTol || start.x > ctx.freeBox.pxHi + ctx.feasTol
                || start.z < ctx.freeBox.pzLo - ctx.feasTol || start.z > ctx.freeBox.pzHi + ctx.feasTol)) return Double.NaN;
        return Scoring.verifiedObjectiveAt(ctx.model, ctx.scenario, ctx.spec, full, start.x, start.z, ctx.feasTol);
    }

    private static Vec3dCore startFullVec(GraphContext ctx, Bucket bd, double px, double pz) {
        JumpPhysicsInputs sc = ctx.scenario;
        return new Vec3dCore(sc.startPos.x + (px - bd.tx0), sc.startPos.y, sc.startPos.z + (pz - bd.tz0));
    }

    private Vec3dCore startFull(GraphContext ctx, Bucket bd, double px, double pz) {
        return startFullVec(ctx, bd, px, pz);
    }

    private static double[] fullYaws(double yaw0, int lead, double[] tail, int n) {
        double[] full = new double[n];
        for (int t = 0; t < lead; t++) full[t] = yaw0;
        for (int t = lead; t < n; t++) full[t] = tail[t - lead];
        return Angles.wrapAll(full);
    }

    private IlsPolish.Config ilsConfig() {
        IlsPolish.Config cfg = new IlsPolish.Config();
        cfg.perturbMagMin = 0.0055;
        cfg.perturbMagSpan = 9.9945;
        return cfg;
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
        double[] tail;
        double stage1Obj;

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
        final boolean max;
        double[] yaws;
        double startX;
        double startZ;
        double obj = Double.NaN;

        Best(boolean max) {
            this.max = max;
        }

        void consider(double[] candYaws, Vec3dCore start, double candObj) {
            if (Double.isNaN(candObj)) return;
            if (yaws == null || (max ? candObj > obj : candObj < obj)) {
                yaws = candYaws;
                startX = start.x;
                startZ = start.z;
                obj = candObj;
            }
        }
    }
}
