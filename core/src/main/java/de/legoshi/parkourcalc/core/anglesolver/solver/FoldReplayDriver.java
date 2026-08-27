package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FoldReplayDriver {

    public static boolean DEBUG = false;

    private static final int MAX_ROUNDS = 6;
    private static final int KEEP_ALIVE_RETRIES = 2;
    private static final double GAP_ACCEPT = 1.0e-5;
    private static final double INFEAS_VALUE = -1.0e6;
    private static final double NARROW_TOL = 1.0e-4;
    private static final double NARROW_DELTA0 = 1.0472;
    private static final double NARROW_SHRINK = 0.7;
    private static final double NARROW_DELTA_FLOOR = 2.0e-5;
    private static final int NARROW_MAX_ITER = 30;
    private static final int TAIL_GATE_MAX_PAIRS = 3;
    private static final double GNOISE = 1.0e-9;
    private static final double END_POLISH_CAP = 0.1;
    private static final double FREE_START_SMOOTH = 5.0e-4;
    private static final double COMBINED_INERTIA_SQ = 9.0e-6;
    private static final double DEGENERATE_NORM = 1.0e-12;
    private static final double TRANSLATE_VIOL_CAP = 1.0e-3;
    private static final double SLP_TRIGGER = 1.0e-3;
    private static final double MARGIN_ANCHOR_CAP = 0.5;
    private static final double OBJ_STABLE_EPS = 1.0e-9;

    private FoldReplayDriver() {
    }

    public static final class Round {
        public final int index;
        public final double linearBound;
        public final double objective;
        public final double maxViolation;
        public final double[] yawsDeg;
        public final double px;
        public final double pz;
        public final boolean[] zeroX;
        public final boolean[] zeroZ;
        public final int patternEvents;
        public final boolean polished;

        Round(int index, double linearBound, double objective, double maxViolation, double[] yawsDeg,
              double px, double pz, boolean[] zeroX, boolean[] zeroZ, int patternEvents, boolean polished) {
            this.index = index;
            this.linearBound = linearBound;
            this.objective = objective;
            this.maxViolation = maxViolation;
            this.yawsDeg = yawsDeg;
            this.px = px;
            this.pz = pz;
            this.zeroX = zeroX;
            this.zeroZ = zeroZ;
            this.patternEvents = patternEvents;
            this.polished = polished;
        }

        public boolean feasible() {
            return maxViolation == 0.0;
        }
    }

    public static final class Result {
        public final List<Round> rounds;
        public final Round best;
        public final boolean fixedPoint;
        public final boolean trivialInfeasible;

        Result(List<Round> rounds, Round best, boolean fixedPoint, boolean trivialInfeasible) {
            this.rounds = Collections.unmodifiableList(rounds);
            this.best = best;
            this.fixedPoint = fixedPoint;
            this.trivialInfeasible = trivialInfeasible;
        }
    }

    private static final class KeepAlivePin {
        final int axis;
        final int tick;
        final boolean positive;

        KeepAlivePin(int axis, int tick, boolean positive) {
            this.axis = axis;
            this.tick = tick;
            this.positive = positive;
        }
    }

    public static final class Params {
        public Map<String, Double> wallTighten;
        public List<JumpLinearModel.Wall> extraWalls;
        public double specWallRelax;
        public double clearance;
        public Set<String> clearanceWalls;
        public boolean[] seedZeroX;
        public boolean[] seedZeroZ;
        public double[] seedYaws;
        public AtomicBoolean cancel;
        public long deadlineNanos;
        public int objectiveRounds;
    }

    public static Result solve(ExactJumpModel exact, JumpSpec spec) {
        return solve(exact, spec, null, null, null, 0L);
    }

    public static Result solve(ExactJumpModel exact, JumpSpec spec, Map<String, Double> wallTighten,
                               List<JumpLinearModel.Wall> extraWalls, AtomicBoolean cancel, long deadlineNanos) {
        Params p = new Params();
        p.wallTighten = wallTighten;
        p.extraWalls = extraWalls;
        p.cancel = cancel;
        p.deadlineNanos = deadlineNanos;
        return solve(exact, spec, p);
    }

    public static Result solve(ExactJumpModel exact, JumpSpec spec, Params params) {
        Map<String, Double> wallTighten = params.wallTighten;
        List<JumpLinearModel.Wall> extraWalls = params.extraWalls;
        AtomicBoolean cancel = params.cancel;
        long deadlineNanos = params.deadlineNanos;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        StartBox box = sc.startBox;
        boolean free = box != null && box.startFree();
        CostateDualSolver.FreeP0 freeP0 = free ? freeStartTerm(box, spec.objective) : null;
        double refPx = box != null ? box.px : sc.startPos.x;
        double refPz = box != null ? box.pz : sc.startPos.z;

        List<Round> rounds = new ArrayList<>();
        List<boolean[][]> seenPatterns = new ArrayList<>();
        boolean[] curZeroX = null;
        boolean[] curZeroZ = null;
        if (params.seedZeroX != null && params.seedZeroZ != null) {
            curZeroX = params.seedZeroX.clone();
            curZeroZ = params.seedZeroZ.clone();
            seenPatterns.add(new boolean[][]{curZeroX.clone(), curZeroZ.clone()});
        }
        List<KeepAlivePin> pins = new ArrayList<>();
        int keepAliveRetries = 0;
        double[] prevYaws = params.seedYaws != null ? params.seedYaws.clone() : null;
        boolean fixedPoint = false;
        boolean trivial = false;
        Round best = null;
        Map<String, Double> margins = new HashMap<>();

        for (int round = 0; round < MAX_ROUNDS; round++) {
            if (cancel != null && cancel.get()) break;
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;

            JumpLinearModel lin = curZeroX == null
                    ? new JumpLinearModel(sc)
                    : new JumpLinearModel(sc, curZeroX, curZeroZ);
            double[] cx = new double[n];
            double[] cz = new double[n];
            lin.objectiveVectors(spec.objective, cx, cz);
            boolean[] trivialFlag = {false};
            List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivialFlag);
            if (trivialFlag[0]) {
                trivial = true;
                break;
            }
            walls = adjustWalls(walls, combineTighten(wallTighten, margins), params.specWallRelax,
                    params.clearance, params.clearanceWalls);
            if (curZeroX != null) walls.addAll(lin.velocityWalls(exact.inertiaThreshold()));
            if (extraWalls != null) walls.addAll(extraWalls);
            for (KeepAlivePin pin : pins) {
                JumpLinearModel.Wall w = lin.keepAliveWall(pin.axis, pin.tick, exact.inertiaThreshold(), pin.positive);
                if (w != null) walls.add(w);
            }

            KernelStats stats = new KernelStats();
            ContinuousSolution sol = solveKernel(n, cx, cz, lin.mMagAll(), walls, freeP0, true,
                    lin, cancel, deadlineNanos, stats);
            if (sol == null) break;
            double bound = sol.value;
            double dvx = sol.dvx;
            double dvz = sol.dvz;

            double[] yaws = decodeYaws(lin, sol.ux, sol.uz, prevYaws);
            double px = free ? clamp(refPx + dvx, box.pxLo, box.pxHi) : refPx;
            double pz = free ? clamp(refPz + dvz, box.pzLo, box.pzHi) : refPz;

            JumpPhysicsInputs scRep = pinnedCopy(sc, px, pz);
            double[] gf = scRep.toGameFacings(yaws);
            ForwardPath path = exact.forward(scRep, gf);
            double obj = path.getPos(spec.objective.tick, spec.objective.axis);
            double viol = compiled.maxViolation(gf, path);

            boolean[] newZeroX = new boolean[n];
            boolean[] newZeroZ = new boolean[n];
            extractPattern(exact, path, n, newZeroX, newZeroZ);

            Round rec = new Round(rounds.size(), bound, obj, viol, yaws, px, pz,
                    newZeroX.clone(), newZeroZ.clone(), countEvents(newZeroX, newZeroZ), false);
            rounds.add(rec);
            best = pickBetter(best, rec, max);
            prevYaws = yaws;
            if (DEBUG) {
                System.out.printf("  FOLD[%d] bound=%.6f obj=%.9f viol=%.3e events=%d%n",
                        rec.index, bound, obj, viol, rec.patternEvents);
            }

            boolean samePattern = curZeroX != null
                    && Arrays.equals(newZeroX, curZeroX) && Arrays.equals(newZeroZ, curZeroZ);
            if (samePattern) {
                fixedPoint = true;
                if (viol == 0.0) {
                    Round prevRound = rounds.size() >= 2 ? rounds.get(rounds.size() - 2) : null;
                    if (prevRound != null && prevRound.feasible()
                            && Math.abs(prevRound.objective - obj) < OBJ_STABLE_EPS) {
                        break;
                    }
                    margins = anchorMargins(lin, spec, yaws, box, px, pz, gf, path, viol);
                    continue;
                }
                double[] curYaws = yaws;
                double[] curGf = gf;
                ForwardPath curPath = path;
                double curPx = px;
                double curPz = pz;
                double curViol = viol;
                boolean landed = false;
                if (viol >= SLP_TRIGGER) {
                    int pre = rounds.size();
                    AnchorSlp.Outcome oc = AnchorSlp.polish(exact, spec, compiled, sc, lin,
                            yaws, gf, path, px, pz, viol, rounds, max);
                    for (int i = pre; i < rounds.size(); i++) best = pickBetter(best, rounds.get(i), max);
                    if (oc.landed != null) {
                        landed = true;
                    } else {
                        curYaws = oc.yaws;
                        curGf = oc.gf;
                        curPath = oc.path;
                        curPx = oc.px;
                        curPz = oc.pz;
                        curViol = oc.viol;
                    }
                }
                if (!landed && free && curViol < TRANSLATE_VIOL_CAP) {
                    boolean[] tzx = new boolean[n];
                    boolean[] tzz = new boolean[n];
                    extractPattern(exact, curPath, n, tzx, tzz);
                    Round pol = translatePolish(exact, spec, compiled, sc, box, curYaws, curGf, curPath,
                            curPx, curPz, tzx, tzz, rounds.size());
                    if (pol != null) {
                        rounds.add(pol);
                        best = pickBetter(best, pol, max);
                        landed = true;
                    }
                }
                if (!landed && curViol < TRANSLATE_VIOL_CAP) {
                    int pre = rounds.size();
                    AnchorSlp.Outcome wo = BucketWalk.walkPolish(exact, spec, compiled, sc, lin,
                            curYaws, curGf, curPath, curPx, curPz, curViol, rounds);
                    for (int i = pre; i < rounds.size(); i++) best = pickBetter(best, rounds.get(i), max);
                    if (wo.landed != null) {
                        landed = true;
                    } else {
                        curYaws = wo.yaws;
                        curGf = wo.gf;
                        curPath = wo.path;
                        curPx = wo.px;
                        curPz = wo.pz;
                        curViol = wo.viol;
                        Round nudged = BucketWalk.ulpNudge(exact, spec, compiled, sc, curYaws,
                                curPx, curPz, curViol, rounds);
                        if (nudged != null) {
                            best = pickBetter(best, nudged, max);
                            landed = true;
                        }
                    }
                }
                if (landed) break;
                margins = anchorMargins(lin, spec, yaws, box, px, pz, gf, path, viol);
                continue;
            }

            if (containsPattern(seenPatterns, newZeroX, newZeroZ) && curZeroX != null) {
                if (keepAliveRetries >= KEEP_ALIVE_RETRIES) {
                    List<int[]> flips = new ArrayList<>();
                    for (int t = 0; t < n && flips.size() < TAIL_GATE_MAX_PAIRS; t++) {
                        if (newZeroX[t] != curZeroX[t]) flips.add(new int[]{0, t});
                        if (flips.size() < TAIL_GATE_MAX_PAIRS && newZeroZ[t] != curZeroZ[t]) {
                            flips.add(new int[]{1, t});
                        }
                    }
                    if (!flips.isEmpty()) {
                        int pre = rounds.size();
                        tailGateEnumeration(exact, spec, compiled, sc, box, free, freeP0, refPx, refPz,
                                params, combineTighten(wallTighten, margins), extraWalls, curZeroX, curZeroZ,
                                flips, path, prevYaws, rounds, cancel, deadlineNanos);
                        for (int i = pre; i < rounds.size(); i++) best = pickBetter(best, rounds.get(i), max);
                    }
                    break;
                }
                keepAliveRetries++;
                boolean pinned = false;
                for (int t = 0; t < n; t++) {
                    if (newZeroX[t] && !curZeroX[t]) {
                        pins.add(new KeepAlivePin(0, t, path.velX[t] >= 0.0));
                        pinned = true;
                    }
                    if (newZeroZ[t] && !curZeroZ[t]) {
                        pins.add(new KeepAlivePin(1, t, path.velZ[t] >= 0.0));
                        pinned = true;
                    }
                }
                if (!pinned) break;
                margins = anchorMargins(lin, spec, yaws, box, px, pz, gf, path, viol);
                continue;
            }

            seenPatterns.add(new boolean[][]{newZeroX.clone(), newZeroZ.clone()});
            curZeroX = newZeroX;
            curZeroZ = newZeroZ;
            pins.clear();
            JumpLinearModel nextLin = new JumpLinearModel(sc, curZeroX, curZeroZ);
            margins = anchorMargins(nextLin, spec, yaws, box, px, pz, gf, path, viol);
        }

        boolean wantEndPolish = best != null
                && ((!best.feasible() && best.maxViolation <= END_POLISH_CAP)
                    || (best.feasible() && params.objectiveRounds > 0));
        if (wantEndPolish
                && (cancel == null || !cancel.get())
                && (deadlineNanos == 0L || System.nanoTime() < deadlineNanos)) {
            Result pol = polishFromAnchor(exact, spec, best.yawsDeg, best.px, best.pz, params);
            rounds.addAll(pol.rounds);
            best = pickBetter(best, pol.best, max);
        }

        return new Result(rounds, best, fixedPoint, trivial);
    }

    public static Result polishFromAnchor(ExactJumpModel exact, JumpSpec spec, double[] anchorYawsAbs,
                                          Double anchorPx, Double anchorPz, Params params) {
        if (params == null) params = new Params();
        Result first = polishPass(exact, spec, anchorYawsAbs, anchorPx, anchorPz, params);
        Round fb = first.best;
        if (fb == null || fb.feasible()) return first;
        boolean improved = fb.maxViolation < first.rounds.get(0).maxViolation;
        if (!improved) return first;
        if (params.cancel != null && params.cancel.get()) return first;
        if (params.deadlineNanos != 0L && System.nanoTime() >= params.deadlineNanos) return first;
        Result second = polishPass(exact, spec, fb.yawsDeg, fb.px, fb.pz, params);
        if (second.best == null) return first;
        List<Round> merged = new ArrayList<>(first.rounds);
        merged.addAll(second.rounds);
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        return new Result(merged, pickBetter(fb, second.best, max), false, false);
    }

    private static Result polishPass(ExactJumpModel exact, JumpSpec spec, double[] anchorYawsAbs,
                                     Double anchorPx, Double anchorPz, Params params) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        StartBox box = sc.startBox;
        boolean free = box != null && box.startFree();
        double refPx = box != null ? box.px : sc.startPos.x;
        double refPz = box != null ? box.pz : sc.startPos.z;
        double px = anchorPx != null ? anchorPx : refPx;
        double pz = anchorPz != null ? anchorPz : refPz;
        if (free) {
            px = clamp(px, box.pxLo, box.pxHi);
            pz = clamp(pz, box.pzLo, box.pzHi);
        }
        double[] yaws = Angles.wrapAll(anchorYawsAbs);

        List<Round> rounds = new ArrayList<>();
        JumpPhysicsInputs scRep = pinnedCopy(sc, px, pz);
        double[] gf = scRep.toGameFacings(yaws);
        ForwardPath path = exact.forward(scRep, gf);
        double viol = compiled.maxViolation(gf, path);
        boolean[] zx = new boolean[n];
        boolean[] zz = new boolean[n];
        extractPattern(exact, path, n, zx, zz);
        Round anchor = new Round(0, Double.NaN, path.getPos(spec.objective.tick, spec.objective.axis),
                viol, yaws.clone(), px, pz, zx.clone(), zz.clone(), countEvents(zx, zz), false);
        rounds.add(anchor);
        Round best = anchor;
        JumpLinearModel lin = new JumpLinearModel(sc, zx, zz);

        if (viol > 0.0 && free) {
            Round pol = translatePolish(exact, spec, compiled, sc, box, yaws, gf, path, px, pz,
                    zx, zz, rounds.size());
            if (pol != null) {
                rounds.add(pol);
                best = pickBetter(best, pol, max);
                px = pol.px;
                pz = pol.pz;
                scRep = pinnedCopy(sc, px, pz);
                gf = scRep.toGameFacings(yaws);
                path = exact.forward(scRep, gf);
                viol = pol.maxViolation;
            }
        }
        if (viol > 0.0) {
            int pre = rounds.size();
            AnchorSlp.Outcome oc = AnchorSlp.polish(exact, spec, compiled, sc, lin, yaws, gf, path,
                    px, pz, viol, rounds, max);
            for (int i = pre; i < rounds.size(); i++) best = pickBetter(best, rounds.get(i), max);
            yaws = oc.landed != null ? oc.landed.yawsDeg : oc.yaws;
            gf = oc.gf;
            path = oc.path;
            px = oc.landed != null ? oc.landed.px : oc.px;
            pz = oc.landed != null ? oc.landed.pz : oc.pz;
            viol = oc.viol;
        }
        if (viol > 0.0) {
            int pre = rounds.size();
            AnchorSlp.Outcome wo = BucketWalk.walkPolish(exact, spec, compiled, sc, lin,
                    yaws, gf, path, px, pz, viol, rounds);
            for (int i = pre; i < rounds.size(); i++) best = pickBetter(best, rounds.get(i), max);
            yaws = wo.landed != null ? wo.landed.yawsDeg : wo.yaws;
            gf = wo.gf;
            path = wo.path;
            px = wo.landed != null ? wo.landed.px : wo.px;
            pz = wo.landed != null ? wo.landed.pz : wo.pz;
            viol = wo.viol;
        }
        if (viol > 0.0) {
            int pre = rounds.size();
            AnchorSlp.Outcome so = BucketWalk.windowScan(exact, spec, compiled, sc, lin,
                    yaws, px, pz, viol, max, rounds);
            for (int i = pre; i < rounds.size(); i++) best = pickBetter(best, rounds.get(i), max);
            yaws = so.landed != null ? so.landed.yawsDeg : so.yaws;
            gf = so.gf;
            path = so.path;
            viol = so.viol;
            if (viol > 0.0 && so.landed == null) {
                pre = rounds.size();
                AnchorSlp.Outcome wo2 = BucketWalk.walkPolish(exact, spec, compiled, sc, lin,
                        yaws, gf, path, px, pz, viol, rounds);
                for (int i = pre; i < rounds.size(); i++) best = pickBetter(best, rounds.get(i), max);
                yaws = wo2.landed != null ? wo2.landed.yawsDeg : wo2.yaws;
                px = wo2.landed != null ? wo2.landed.px : wo2.px;
                pz = wo2.landed != null ? wo2.landed.pz : wo2.pz;
                viol = wo2.viol;
            }
        }
        if (viol > 0.0 && viol < TRANSLATE_VIOL_CAP && free) {
            boolean[] tzx = new boolean[n];
            boolean[] tzz = new boolean[n];
            extractPattern(exact, path, n, tzx, tzz);
            Round pol = translatePolish(exact, spec, compiled, sc, box, yaws, gf, path, px, pz,
                    tzx, tzz, rounds.size());
            if (pol != null) {
                rounds.add(pol);
                best = pickBetter(best, pol, max);
                viol = 0.0;
            }
        }
        if (viol > 0.0) {
            Round nd = BucketWalk.ulpNudge(exact, spec, compiled, sc, yaws, px, pz, viol, rounds);
            if (nd != null) best = pickBetter(best, nd, max);
        }
        if (best != null && best.feasible()) {
            int objRounds = params.objectiveRounds > 0 ? params.objectiveRounds : BucketWalk.OBJ_ROUNDS;
            double sense = max ? 1.0 : -1.0;
            Round feas = best;
            for (int cycle = 0; cycle < 4; cycle++) {
                JumpLinearModel olin = new JumpLinearModel(sc, feas.zeroX, feas.zeroZ);
                Round improved = BucketWalk.objPolish(exact, spec, compiled, sc, olin, feas, rounds, max, objRounds);
                best = pickBetter(best, improved, max);
                int pre = rounds.size();
                AnchorSlp.Outcome so = BucketWalk.windowScan(exact, spec, compiled, sc, olin,
                        improved.yawsDeg, improved.px, improved.pz, 0.0, max, rounds);
                for (int i = pre; i < rounds.size(); i++) best = pickBetter(best, rounds.get(i), max);
                if (so.landed == null) {
                    feas = improved;
                    break;
                }
                double gain = sense * (so.landed.objective - improved.objective);
                feas = so.landed;
                if (gain <= 1.0e-12) break;
            }
        }
        return new Result(rounds, best, false, false);
    }

    private static void tailGateEnumeration(ExactJumpModel exact, JumpSpec spec,
                                            JumpConstraintCompiler.Compiled compiled, JumpPhysicsInputs sc,
                                            StartBox box, boolean free, CostateDualSolver.FreeP0 freeP0,
                                            double refPx, double refPz, Params params,
                                            Map<String, Double> tighten,
                                            List<JumpLinearModel.Wall> extraWalls,
                                            boolean[] baseZeroX, boolean[] baseZeroZ, List<int[]> flips,
                                            ForwardPath lastPath, double[] prevYaws, List<Round> rounds,
                                            AtomicBoolean cancel, long deadlineNanos) {
        int n = sc.numTicks;
        int k = flips.size();
        for (int combo = 0; combo < (1 << k); combo++) {
            if (cancel != null && cancel.get()) return;
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) return;
            boolean[] zx = baseZeroX.clone();
            boolean[] zz = baseZeroZ.clone();
            for (int i = 0; i < k; i++) {
                boolean zero = (combo & (1 << i)) != 0;
                int[] f = flips.get(i);
                if (f[0] == 0) zx[f[1]] = zero;
                else zz[f[1]] = zero;
            }
            if (Arrays.equals(zx, baseZeroX) && Arrays.equals(zz, baseZeroZ)) continue;
            JumpLinearModel lin = new JumpLinearModel(sc, zx, zz);
            double[] cx = new double[n];
            double[] cz = new double[n];
            lin.objectiveVectors(spec.objective, cx, cz);
            boolean[] trivialFlag = {false};
            List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivialFlag);
            if (trivialFlag[0]) continue;
            walls = adjustWalls(walls, tighten, params.specWallRelax, params.clearance, params.clearanceWalls);
            walls.addAll(lin.velocityWalls(exact.inertiaThreshold()));
            if (extraWalls != null) walls.addAll(extraWalls);
            for (int i = 0; i < k; i++) {
                boolean zero = (combo & (1 << i)) != 0;
                if (zero) continue;
                int[] f = flips.get(i);
                double v = f[0] == 0 ? lastPath.velX[f[1]] : lastPath.velZ[f[1]];
                JumpLinearModel.Wall w = lin.keepAliveWall(f[0], f[1], exact.inertiaThreshold(), v >= 0.0);
                if (w != null) walls.add(w);
            }
            ContinuousSolution sol = solveKernel(n, cx, cz, lin.mMagAll(), walls, freeP0, false,
                    lin, cancel, deadlineNanos, null);
            if (sol == null) continue;
            double[] yaws = decodeYaws(lin, sol.ux, sol.uz, prevYaws);
            double px = free ? clamp(refPx + sol.dvx, box.pxLo, box.pxHi) : refPx;
            double pz = free ? clamp(refPz + sol.dvz, box.pzLo, box.pzHi) : refPz;
            JumpPhysicsInputs scRep = pinnedCopy(sc, px, pz);
            double[] gf = scRep.toGameFacings(yaws);
            ForwardPath path = exact.forward(scRep, gf);
            double obj = path.getPos(spec.objective.tick, spec.objective.axis);
            double viol = compiled.maxViolation(gf, path);
            boolean[] rzx = new boolean[n];
            boolean[] rzz = new boolean[n];
            extractPattern(exact, path, n, rzx, rzz);
            rounds.add(new Round(rounds.size(), sol.value, obj, viol, yaws, px, pz,
                    rzx, rzz, countEvents(rzx, rzz), false));
            if (DEBUG) {
                System.out.printf("  TAILGATE combo=%d obj=%.9f viol=%.3e%n", combo, obj, viol);
            }
        }
    }

    private static final class ContinuousSolution {
        final double[] gx;
        final double[] gz;
        final double[] ux;
        final double[] uz;
        final double value;
        final double dvx;
        final double dvz;

        ContinuousSolution(double[] gx, double[] gz, double[] ux, double[] uz, double value,
                           double dvx, double dvz) {
            this.gx = gx;
            this.gz = gz;
            this.ux = ux;
            this.uz = uz;
            this.value = value;
            this.dvx = dvx;
            this.dvz = dvz;
        }
    }

    static final class KernelStats {
        int solves;
        int chordTicks;
        boolean infeasible;
    }

    private static boolean usable(DiskSocpKernel.Outcome oc) {
        if (oc.result == null || oc.failCode == DiskSocpKernel.FAIL_UNBOUNDED) return false;
        if (oc.result.value < INFEAS_VALUE) return false;
        if (oc.result.converged) return true;
        return oc.result.gap <= GAP_ACCEPT;
    }

    private static boolean infeasibleSignal(DiskSocpKernel.Outcome oc) {
        if (oc.failCode == DiskSocpKernel.FAIL_UNBOUNDED) return true;
        return oc.result != null && oc.result.value < INFEAS_VALUE;
    }

    private static Map<String, Double> lambdaMap(List<JumpLinearModel.Wall> walls,
                                                 List<DiskSocpKernel.ChordRow> chordRows,
                                                 DiskSocpKernel.Result r) {
        Map<String, Double> map = new HashMap<>();
        int mw = walls.size();
        for (int j = 0; j < mw && j < r.lambda.length; j++) map.put(walls.get(j).name, r.lambda[j]);
        if (chordRows != null) {
            for (int q = 0; q < chordRows.size() && mw + q < r.lambda.length; q++) {
                map.put(chordRows.get(q).name, r.lambda[mw + q]);
            }
        }
        return map;
    }

    private static List<DiskSocpKernel.ChordRow> chordRows(java.util.TreeMap<Integer, double[]> chords,
                                                           double[] mMag) {
        List<DiskSocpKernel.ChordRow> rows = new ArrayList<>(chords.size());
        for (Map.Entry<Integer, double[]> e : chords.entrySet()) {
            int t = e.getKey();
            double mu = e.getValue()[0];
            double delta = e.getValue()[1];
            rows.add(new DiskSocpKernel.ChordRow(t, Math.cos(mu), Math.sin(mu),
                    mMag[t] * Math.cos(delta), "chord@" + t));
        }
        return rows;
    }

    private static ContinuousSolution solveKernel(int n, double[] cx, double[] cz, double[] mMag,
                                                  List<JumpLinearModel.Wall> walls,
                                                  CostateDualSolver.FreeP0 freeP0, boolean dualFallback,
                                                  JumpLinearModel lin, AtomicBoolean cancel,
                                                  long deadlineNanos, KernelStats stats) {
        DiskSocpKernel.Outcome base = DiskSocpKernel.solveChords(n, cx, cz, mMag, walls, freeP0, null, null);
        if (stats != null) stats.solves++;
        if (!usable(base)) {
            if (infeasibleSignal(base)) {
                if (stats != null) stats.infeasible = true;
                if (DEBUG) System.out.printf("    KERNEL infeasible walls=%d%n", walls.size());
                return null;
            }
            if (DEBUG) {
                System.out.printf("    KERNEL %s walls=%d%n",
                        base.result == null ? "failed code=" + base.failCode
                                : "unconverged gap=" + base.result.gap, walls.size());
            }
            if (!dualFallback) return null;
            CostateDualSolver.Result dr = new CostateDualSolver(n, cx, cz, mMag, walls, freeP0).solve(0.0, null);
            if (dr == null) return null;
            double[] ux = new double[n];
            double[] uz = new double[n];
            for (int t = 0; t < n; t++) {
                double norm = Math.hypot(dr.gx[t], dr.gz[t]);
                if (norm > 0.0) {
                    ux[t] = mMag[t] * dr.gx[t] / norm;
                    uz[t] = mMag[t] * dr.gz[t] / norm;
                }
            }
            return new ContinuousSolution(dr.gx, dr.gz, ux, uz, dr.value, dr.dvx, dr.dvz);
        }

        DiskSocpKernel.Result sol = base.result;
        java.util.TreeMap<Integer, double[]> chords = new java.util.TreeMap<>();
        Map<String, Double> warm = lambdaMap(walls, null, sol);
        List<DiskSocpKernel.ChordRow> curRows = null;
        for (int it = 0; it < NARROW_MAX_ITER; it++) {
            if (cancel != null && cancel.get()) break;
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
            int worst = -1;
            double worstSlack = 0.0;
            for (int t = 0; t < n; t++) {
                double mm = mMag[t];
                if (mm <= 0.0) continue;
                double slack = mm - Math.hypot(sol.ux[t], sol.uz[t]);
                if (slack > NARROW_TOL * Math.max(mm, 0.026) && (worst < 0 || slack > worstSlack)) {
                    worst = t;
                    worstSlack = slack;
                }
            }
            if (worst < 0) break;
            java.util.TreeMap<Integer, double[]> next = new java.util.TreeMap<>();
            for (Map.Entry<Integer, double[]> e : chords.entrySet()) next.put(e.getKey(), e.getValue().clone());
            for (int t = 0; t < n; t++) {
                double mm = mMag[t];
                if (mm <= 0.0) continue;
                double slack = mm - Math.hypot(sol.ux[t], sol.uz[t]);
                double frac = slack / Math.max(mm, 1.0e-12);
                double dir = Math.hypot(sol.ux[t], sol.uz[t]) < 1.0e-12
                        ? lin.baseArg(t) : Math.atan2(sol.uz[t], sol.ux[t]);
                double[] cur = next.get(t);
                if (cur != null) {
                    double nd = frac > NARROW_TOL ? cur[1] * NARROW_SHRINK : cur[1];
                    next.put(t, new double[]{dir, Math.max(nd, NARROW_DELTA_FLOOR)});
                } else if (frac > NARROW_TOL) {
                    next.put(t, new double[]{dir, NARROW_DELTA0});
                }
            }
            List<DiskSocpKernel.ChordRow> rows = chordRows(next, mMag);
            DiskSocpKernel.Outcome cand = DiskSocpKernel.solveChords(n, cx, cz, mMag, walls, freeP0, rows, warm);
            if (stats != null) stats.solves++;
            if (DEBUG) {
                System.out.printf("      narrow[%d] worst=%d slack=%.3e chords=%d usable=%b gap=%s%n",
                        it, worst, worstSlack, next.size(), usable(cand),
                        cand.result == null ? "null" : String.format("%.2e", cand.result.gap));
            }
            if (!usable(cand)) {
                java.util.TreeMap<Integer, double[]> loose = new java.util.TreeMap<>();
                for (Map.Entry<Integer, double[]> e : next.entrySet()) {
                    loose.put(e.getKey(), new double[]{e.getValue()[0],
                            Math.min(e.getValue()[1] / NARROW_SHRINK, NARROW_DELTA0)});
                }
                rows = chordRows(loose, mMag);
                cand = DiskSocpKernel.solveChords(n, cx, cz, mMag, walls, freeP0, rows, warm);
                if (stats != null) stats.solves++;
                if (DEBUG) {
                    System.out.printf("      narrow[%d] loosened usable=%b%n", it, usable(cand));
                }
                if (!usable(cand)) break;
                next = loose;
            }
            chords = next;
            curRows = rows;
            sol = cand.result;
            warm = lambdaMap(walls, curRows, sol);
        }
        if (stats != null) stats.chordTicks = chords.size();
        if (DEBUG && !chords.isEmpty()) {
            System.out.printf("    NARROW ticks=%d solves=%d%n", chords.size(), stats == null ? -1 : stats.solves);
        }
        return new ContinuousSolution(sol.gx, sol.gz, sol.ux, sol.uz, sol.value, sol.dvx, sol.dvz);
    }

    private static CostateDualSolver.FreeP0 freeStartTerm(StartBox box, Objective obj) {
        boolean max = obj.sense == Objective.Sense.MAX;
        double objDevX = obj.axis == JumpPhysicsInputs.Axis.X ? (max ? 1.0 : -1.0) : 0.0;
        double objDevZ = obj.axis == JumpPhysicsInputs.Axis.X ? 0.0 : (max ? 1.0 : -1.0);
        return new CostateDualSolver.FreeP0(box.pxLo - box.px, box.pxHi - box.px,
                box.pzLo - box.pz, box.pzHi - box.pz, objDevX, objDevZ, FREE_START_SMOOTH);
    }

    private static double[] decodeYaws(JumpLinearModel lin, double[] ux, double[] uz, double[] prevYaws) {
        int n = lin.n;
        double[] yaws = new double[n];
        double last = 0.0;
        for (int t = 0; t < n; t++) {
            double norm = Math.hypot(ux[t], uz[t]);
            if (lin.mMag(t) <= 0.0 || norm < DEGENERATE_NORM) {
                yaws[t] = prevYaws != null ? prevYaws[t] : last;
            } else {
                yaws[t] = lin.recoverYawDeg(t, ux[t], uz[t]);
            }
            last = yaws[t];
        }
        return yaws;
    }

    public static void extractPattern(ExactJumpModel exact, ForwardPath path, int n,
                                      boolean[] outZeroX, boolean[] outZeroZ) {
        double thr = exact.inertiaThreshold();
        boolean perAxis = exact.perAxisInertia();
        for (int t = 0; t < n; t++) {
            double vx = path.velX[t];
            double vz = path.velZ[t];
            if (perAxis) {
                outZeroX[t] = Math.abs(vx) < thr;
                outZeroZ[t] = Math.abs(vz) < thr;
            } else {
                boolean both = vx * vx + vz * vz < COMBINED_INERTIA_SQ;
                outZeroX[t] = both;
                outZeroZ[t] = both;
            }
        }
    }

    private static Round translatePolish(ExactJumpModel exact, JumpSpec spec,
                                         JumpConstraintCompiler.Compiled compiled, JumpPhysicsInputs sc,
                                         StartBox box, double[] yaws, double[] gf, ForwardPath path,
                                         double px, double pz, boolean[] zeroX, boolean[] zeroZ, int index) {
        double loX = box.pxLo - px;
        double hiX = box.pxHi - px;
        double loZ = box.pzLo - pz;
        double hiZ = box.pzHi - pz;
        for (JumpConstraint c : spec.constraints) {
            if (c.mode != JumpConstraint.Mode.X && c.mode != JumpConstraint.Mode.Z) {
                if (JumpConstraintCompiler.slack(c, gf, path) > 0.0) return null;
                continue;
            }
            int tc = c.t2 == null ? 1 : (c.op == JumpConstraint.Op.PLUS ? 2 : 0);
            if (tc == 0) {
                if (JumpConstraintCompiler.slack(c, gf, path) > 0.0) return null;
                continue;
            }
            double e = JumpConstraintCompiler.evaluate(c, gf, path);
            boolean axisX = c.mode == JumpConstraint.Mode.X;
            if (c.cmp == JumpConstraint.Cmp.GE) {
                double lo = -e / tc;
                if (axisX) loX = Math.max(loX, lo);
                else loZ = Math.max(loZ, lo);
            } else if (c.cmp == JumpConstraint.Cmp.LE) {
                double hi = -e / tc;
                if (axisX) hiX = Math.min(hiX, hi);
                else hiZ = Math.min(hiZ, hi);
            } else {
                if (e != 0.0) return null;
                if (axisX) {
                    loX = Math.max(loX, 0.0);
                    hiX = Math.min(hiX, 0.0);
                } else {
                    loZ = Math.max(loZ, 0.0);
                    hiZ = Math.min(hiZ, 0.0);
                }
            }
        }
        if (loX > hiX || loZ > hiZ) return null;
        double dpx = 0.5 * (loX + hiX);
        double dpz = 0.5 * (loZ + hiZ);
        double npx = px + dpx;
        double npz = pz + dpz;
        JumpPhysicsInputs scRep = pinnedCopy(sc, npx, npz);
        double[] gf2 = scRep.toGameFacings(yaws);
        ForwardPath path2 = exact.forward(scRep, gf2);
        double viol = compiled.maxViolation(gf2, path2);
        if (viol != 0.0) return null;
        double obj = path2.getPos(spec.objective.tick, spec.objective.axis);
        if (DEBUG) {
            System.out.printf("  FOLD[%d] translate dpx=%.3e dpz=%.3e obj=%.9f%n", index, dpx, dpz, obj);
        }
        return new Round(index, Double.NaN, obj, viol, yaws.clone(), npx, npz,
                zeroX.clone(), zeroZ.clone(), countEvents(zeroX, zeroZ), true);
    }

    static JumpPhysicsInputs pinnedCopy(JumpPhysicsInputs sc, double px, double pz) {
        JumpPhysicsInputs c = sc.copy();
        c.startPos = new Vec3dCore(px, sc.startPos.y, pz);
        c.startBox = StartBox.pinned(px, pz, sc.initialVelocity.x, sc.initialVelocity.z);
        return c;
    }

    private static List<JumpLinearModel.Wall> adjustWalls(List<JumpLinearModel.Wall> walls,
                                                          Map<String, Double> tighten, double relax,
                                                          double clearance, Set<String> clearanceWalls) {
        boolean anyTighten = tighten != null && !tighten.isEmpty();
        boolean anyClear = clearance != 0.0 && clearanceWalls != null && !clearanceWalls.isEmpty();
        if (!anyTighten && relax == 0.0 && !anyClear) return walls;
        List<JumpLinearModel.Wall> out = new ArrayList<>(walls.size());
        for (JumpLinearModel.Wall w : walls) {
            double shift = 0.0;
            if (!w.eq) {
                shift += relax;
                if (anyClear && clearanceWalls.contains(w.name)) shift -= clearance;
                if (anyTighten) {
                    Double d = tighten.get(w.name);
                    if (d != null) shift -= d;
                }
            }
            if (shift == 0.0) {
                out.add(w);
            } else {
                out.add(new JumpLinearModel.Wall(w.axis, w.coef, w.bPrime + shift, w.eq, w.name, w.p0coef));
            }
        }
        return out;
    }

    private static Map<String, Double> anchorMargins(JumpLinearModel lin, JumpSpec spec, double[] yaws,
                                                     StartBox box, double px, double pz, double[] gf,
                                                     ForwardPath path, double viol) {
        if (viol > MARGIN_ANCHOR_CAP) return new HashMap<>();
        return AnchorSlp.margins(lin, spec.constraints, yaws, box, px, pz, gf, path);
    }

    private static Map<String, Double> combineTighten(Map<String, Double> caller, Map<String, Double> margins) {
        boolean noMargins = margins == null || margins.isEmpty();
        boolean noCaller = caller == null || caller.isEmpty();
        if (noMargins) return caller;
        if (noCaller) return margins;
        Map<String, Double> out = new HashMap<>(margins);
        for (Map.Entry<String, Double> e : caller.entrySet()) {
            Double prev = out.get(e.getKey());
            out.put(e.getKey(), prev == null ? e.getValue() : prev + e.getValue());
        }
        return out;
    }

    private static boolean containsPattern(List<boolean[][]> seen, boolean[] zx, boolean[] zz) {
        for (boolean[][] p : seen) {
            if (Arrays.equals(p[0], zx) && Arrays.equals(p[1], zz)) return true;
        }
        return false;
    }

    static int countEvents(boolean[] zx, boolean[] zz) {
        int c = 0;
        for (boolean b : zx) if (b) c++;
        for (boolean b : zz) if (b) c++;
        return c;
    }

    static Round pickBetter(Round a, Round b, boolean max) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.feasible() != b.feasible()) return a.feasible() ? a : b;
        if (a.feasible()) {
            return betterObj(b.objective, a.objective, max) ? b : a;
        }
        if (b.maxViolation < a.maxViolation) return b;
        if (b.maxViolation > a.maxViolation) return a;
        return betterObj(b.objective, a.objective, max) ? b : a;
    }

    private static boolean betterObj(double a, double b, boolean max) {
        return max ? a > b : a < b;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
