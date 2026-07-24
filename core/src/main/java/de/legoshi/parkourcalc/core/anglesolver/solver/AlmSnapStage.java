package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AlmSnapStage {

    private static final boolean DEBUG = "1".equals(System.getenv("PKC_ALM_DEBUG"));
    private static final int MAX_SNAPS = 6;
    private static final double DEDUP_TOL_RAD = 1.0e-3;

    public static final class SeedStat {
        public final int index;
        public final String kind;
        public final double almSmoothViol;
        public final double almSmoothObjective;
        public final double snapViol;
        public final double snapObjective;
        public final boolean snapFeasible;
        public final boolean almOnly;
        public final long millis;

        SeedStat(int index, String kind, double almSmoothViol, double almSmoothObjective,
                 double snapViol, double snapObjective, boolean snapFeasible, boolean almOnly, long millis) {
            this.index = index;
            this.kind = kind;
            this.almSmoothViol = almSmoothViol;
            this.almSmoothObjective = almSmoothObjective;
            this.snapViol = snapViol;
            this.snapObjective = snapObjective;
            this.snapFeasible = snapFeasible;
            this.almOnly = almOnly;
            this.millis = millis;
        }
    }

    public static final class SolveOutcome {
        public final double[] yawsDeg;
        public final double viol;
        public final double objective;
        public final boolean feasible;
        public final double tx;
        public final double tz;
        public final double startX;
        public final double startZ;
        public final List<SeedStat> seedStats;
        public final SnapRepairPolish.Counters winnerSnap;
        public final AlmBfgsCore.Counters winnerAlm;
        public final int winnerSeedIndex;
        public final String winnerKind;
        public final int seedsTried;

        SolveOutcome(double[] yawsDeg, double viol, double objective, boolean feasible, double tx, double tz,
                     double startX, double startZ,
                     List<SeedStat> seedStats, SnapRepairPolish.Counters winnerSnap, AlmBfgsCore.Counters winnerAlm,
                     int winnerSeedIndex, String winnerKind, int seedsTried) {
            this.yawsDeg = yawsDeg;
            this.viol = viol;
            this.objective = objective;
            this.feasible = feasible;
            this.tx = tx;
            this.tz = tz;
            this.startX = startX;
            this.startZ = startZ;
            this.seedStats = seedStats;
            this.winnerSnap = winnerSnap;
            this.winnerAlm = winnerAlm;
            this.winnerSeedIndex = winnerSeedIndex;
            this.winnerKind = winnerKind;
            this.seedsTried = seedsTried;
        }
    }

    private static final class AlmEntry {
        final int seedIndex;
        final String kind;
        final AlmBfgsCore.Result alm;
        final long almMs;
        SnapRepairPolish.Result snap;
        long snapMs;
        boolean snapped;

        AlmEntry(int seedIndex, String kind, AlmBfgsCore.Result alm, long almMs) {
            this.seedIndex = seedIndex;
            this.kind = kind;
            this.alm = alm;
            this.almMs = almMs;
        }
    }

    private AlmSnapStage() {
    }

    public static SolveOutcome solve(ExactJumpModel model, JumpSpec spec, List<double[]> warmSeedsAbsDeg,
                                     int constantSeedCount, boolean cooking, int topK, double gateWiden,
                                     double[] transDomain, long deadlineNanos, AtomicBoolean cancel) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        final double objSign = spec.objective.sense == Objective.Sense.MAX ? -1.0 : 1.0;

        List<double[]> seeds = new ArrayList<>();
        List<String> kinds = new ArrayList<>();
        List<Integer> seedIds = new ArrayList<>();
        int[] order = lowDiscrepancyOrder(constantSeedCount);
        for (int idx : order) {
            double angle = 360.0 * idx / constantSeedCount;
            double[] s = new double[n];
            for (int k = 0; k < n; k++) s[k] = angle;
            seeds.add(s);
            kinds.add("constant");
            seedIds.add(idx);
        }
        if (warmSeedsAbsDeg != null) {
            int w = 0;
            for (double[] wv : warmSeedsAbsDeg) {
                seeds.add(wv.clone());
                kinds.add("warm");
                seedIds.add(constantSeedCount + w);
                w++;
            }
        }

        AlmBfgsCore.Config almCfg = new AlmBfgsCore.Config();

        List<AlmEntry> entries = new ArrayList<>();
        for (int i = 0; i < seeds.size(); i++) {
            if (cancel != null && cancel.get()) break;
            if (deadlineNanos > 0 && System.nanoTime() >= deadlineNanos) break;
            long t0 = System.nanoTime();
            double[] seedAbs = seeds.get(i);
            double[] theta = new double[n];
            for (int k = 0; k < n; k++) theta[k] = Math.toRadians(seedAbs[k]);
            AlmBfgsCore.Result alm = AlmBfgsCore.solve(model, spec, theta, almCfg, deadlineNanos, cancel, transDomain);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            entries.add(new AlmEntry(seedIds.get(i), kinds.get(i), alm, ms));
        }

        List<AlmEntry> uniq = new ArrayList<>();
        for (AlmEntry e : entries) {
            boolean dup = false;
            for (AlmEntry u : uniq) {
                if (nearIdentical(e.alm.thetaRad, u.alm.thetaRad, DEDUP_TOL_RAD)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) uniq.add(e);
        }

        int snapCount = Math.min(MAX_SNAPS, uniq.size());

        SnapRepairPolish.Config srpCfg = new SnapRepairPolish.Config();
        srpCfg.cooking = cooking;
        srpCfg.topK = topK;
        srpCfg.candGateWiden = gateWiden;

        double[] bestYaws = null;
        double bestViol = Double.POSITIVE_INFINITY;
        double bestObjRaw = Double.NaN;
        double bestObjSigned = Double.POSITIVE_INFINITY;
        boolean bestFeasible = false;
        double bestTx = 0.0;
        double bestTz = 0.0;
        double bestStartX = sc.startPos.x;
        double bestStartZ = sc.startPos.z;
        SnapRepairPolish.Counters bestSnap = null;
        AlmBfgsCore.Counters bestAlm = null;
        int bestSeedIndex = -1;
        String bestKind = null;
        int snapsRun = 0;

        for (int j = 0; j < snapCount; j++) {
            if (cancel != null && cancel.get()) break;
            long nowNs = System.nanoTime();
            long sliceDeadline = 0L;
            if (deadlineNanos > 0) {
                long remaining = deadlineNanos - nowNs;
                if (remaining <= 0) break;
                int remainingSnaps = snapCount - j;
                sliceDeadline = nowNs + remaining / remainingSnaps;
            }
            AlmEntry en = uniq.get(j);
            double[] almAbs = Angles.wrapAll(toDeg(en.alm.thetaRad));
            JumpSpec snapSpec = spec;
            double[] snapDom = transDomain;
            double baseTx = 0.0;
            double baseTz = 0.0;
            boolean translate = !AlmBfgsCore.isPinnedDomain(transDomain);
            if (translate && (en.alm.tx != 0.0 || en.alm.tz != 0.0)) {
                baseTx = en.alm.tx;
                baseTz = en.alm.tz;
                snapSpec = shiftedSpec(spec, baseTx, baseTz);
                snapDom = new double[]{transDomain[0] - baseTx, transDomain[1] - baseTx,
                        transDomain[2] - baseTz, transDomain[3] - baseTz};
            }
            long s0 = System.nanoTime();
            SnapRepairPolish.Result snap = SnapRepairPolish.run(model, snapSpec, almAbs, srpCfg, sliceDeadline, cancel, snapDom);
            long snapMs = (System.nanoTime() - s0) / 1_000_000L;
            en.snap = snap;
            en.snapMs = snapMs;
            en.snapped = true;
            snapsRun++;

            double totTx = baseTx + snap.tx;
            double totTz = baseTz + snap.tz;
            JumpPhysicsInputs snapSc = snapSpec.asScenario();
            double solvedStartX = snapSc.startPos.x + snap.tx;
            double solvedStartZ = snapSc.startPos.z + snap.tz;
            double signed = objSign * snap.exactObjective + spec.objective.smoothPenalty(snap.gameFacings);
            if (better(snap.feasible, signed, snap.exactViol, bestFeasible, bestObjSigned, bestViol, bestYaws != null)) {
                bestYaws = snap.absYawsDeg;
                bestViol = snap.exactViol;
                bestObjRaw = snap.exactObjective;
                bestObjSigned = signed;
                bestFeasible = snap.feasible;
                bestTx = totTx;
                bestTz = totTz;
                bestStartX = solvedStartX;
                bestStartZ = solvedStartZ;
                bestSnap = snap.counters;
                bestAlm = en.alm.counters;
                bestSeedIndex = en.seedIndex;
                bestKind = en.kind;
            }
            if (DEBUG) {
                System.out.println("[DBG-almsnap] snap rank=" + j + " seed=" + en.seedIndex + " kind=" + en.kind
                        + " almViol=" + en.alm.smoothViol + " snapViol=" + snap.exactViol
                        + " snapObj=" + snap.exactObjective + " feasible=" + snap.feasible
                        + " tx=" + totTx + " tz=" + totTz + " ms=" + snapMs);
            }
        }

        List<SeedStat> stats = new ArrayList<>();
        for (AlmEntry e : entries) {
            double snapViol = e.snapped ? e.snap.exactViol : Double.NaN;
            double snapObj = e.snapped ? e.snap.exactObjective : Double.NaN;
            boolean snapFeas = e.snapped && e.snap.feasible;
            long ms = e.almMs + (e.snapped ? e.snapMs : 0L);
            stats.add(new SeedStat(e.seedIndex, e.kind, e.alm.smoothViol, e.alm.smoothObjective,
                    snapViol, snapObj, snapFeas, !e.snapped, ms));
        }
        stats.sort((a, b) -> Integer.compare(a.index, b.index));

        if (bestYaws == null) {
            bestYaws = new double[n];
        }

        if (DEBUG) {
            System.out.println("[DBG-almsnap] phase1 alm=" + entries.size() + " uniq=" + uniq.size()
                    + " snapCount=" + snapCount + " snapsRun=" + snapsRun + " winnerSeed=" + bestSeedIndex);
        }

        return new SolveOutcome(bestYaws, bestViol, bestObjRaw, bestFeasible, bestTx, bestTz,
                bestStartX, bestStartZ, stats,
                bestSnap, bestAlm, bestSeedIndex, bestKind, snapsRun);
    }

    private static int[] lowDiscrepancyOrder(int count) {
        Integer[] idx = new Integer[count];
        for (int i = 0; i < count; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Double.compare(radicalInverse2(a), radicalInverse2(b)));
        int[] out = new int[count];
        for (int i = 0; i < count; i++) out[i] = idx[i];
        return out;
    }

    private static double radicalInverse2(int i) {
        double r = 0.0;
        double f = 0.5;
        int v = i;
        while (v > 0) {
            r += (v & 1) * f;
            f *= 0.5;
            v >>= 1;
        }
        return r;
    }

    private static boolean nearIdentical(double[] a, double[] b, double tol) {
        if (a.length != b.length) return false;
        double twoPi = 2.0 * Math.PI;
        for (int k = 0; k < a.length; k++) {
            double d = Math.IEEEremainder(a[k] - b[k], twoPi);
            if (Math.abs(d) > tol) return false;
        }
        return true;
    }

    private static boolean better(boolean feas, double signed, double viol,
                                  boolean bestFeas, double bestSigned, double bestViol, boolean hasBest) {
        if (!hasBest) return true;
        if (feas != bestFeas) return feas;
        if (feas) return signed < bestSigned;
        if (viol != bestViol) return viol < bestViol;
        return signed < bestSigned;
    }

    private static double[] toDeg(double[] rad) {
        double[] d = new double[rad.length];
        for (int i = 0; i < rad.length; i++) d[i] = Math.toDegrees(rad[i]);
        return d;
    }

    private static JumpSpec shiftedSpec(JumpSpec spec, double tx, double tz) {
        JumpPhysicsInputs b = spec.asScenario();
        JumpPhysicsInputs a = new JumpPhysicsInputs(b.numTicks);
        a.startPos = new Vec3dCore(b.startPos.x + tx, b.startPos.y, b.startPos.z + tz);
        a.startYaw = b.startYaw;
        a.initialVelocity = b.initialVelocity;
        if (b.startBox != null) {
            a.startBox = StartBox.pinned(a.startPos.x, a.startPos.z, b.startBox.vx, b.startBox.vz);
        }
        a.jumpTick = b.jumpTick;
        a.jumpPerTick = b.jumpPerTick;
        a.strafeSign = b.strafeSign;
        a.strafePerTick = b.strafePerTick;
        a.speedAmplifier = b.speedAmplifier;
        a.slipPerTick = b.slipPerTick;
        a.yawLockedPerTick = b.yawLockedPerTick;
        a.sprintPerTick = b.sprintPerTick;
        a.incomingSprint = b.incomingSprint;
        a.incomingAmp = b.incomingAmp;
        a.forwardInputPerTick = b.forwardInputPerTick;
        a.strafeInputPerTick = b.strafeInputPerTick;
        return new JumpSpec(a, spec.constraints, spec.objective);
    }
}
