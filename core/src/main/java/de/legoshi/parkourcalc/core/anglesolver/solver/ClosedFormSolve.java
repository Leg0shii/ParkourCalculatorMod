package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Closed-form jump solve: the microsecond fast path tried ahead of the slower recovery stages.
 *
 *  <p>It exploits the proven structure (horizontal motion is linear in the per-tick input vectors; the
 *  only nonconvexity is each input's fixed modulus) by solving the convex Lagrangian dual
 *  ({@link CostateDualSolver}) to global optimality and recovering each tick's optimal yaw as the direction
 *  of its friction-propagated costate ({@link JumpLinearModel}). That is the entire continuous solve: a few
 *  microseconds, no search, no tuning.
 *
 *  <p>The continuous optimum hugs the active walls exactly; MC's 65536-bucket sine table then perturbs the
 *  realized path by ~1e-4 blocks, which could nudge a tight wall to the wrong side. So the walls are solved
 *  with a small inward margin and the result is re-checked on the byte-exact {@link ExactJumpModel}; the
 *  margin is grown geometrically until the quantized trajectory is strictly feasible. Each attempt is
 *  microseconds, so even several attempts stay far under a millisecond.
 *
 *  <p>Returns absolute wrapped facings strictly feasible on the exact model, or {@code null} when the
 *  closed form does not apply (facing walls beyond {@link FacingPrefold} pins and dF=0 chains) or cannot
 *  certify feasibility; the caller then falls back
 *  ({@link SlpSolve}, then the recovery stages), so this only ever makes solving faster, never less
 *  reliable. Optimizing into a same-axis wall degenerates the dual's recovery, which is why one
 *  direction can fail here while the opposite certifies (docs/research/angle-solver.md 2.1.1). */
public final class ClosedFormSolve {

    private ClosedFormSolve() {
    }

    public static boolean DEBUG = false;

    public static final class Config {
        /** Inward wall margins (blocks) tried in order; the first that yields exact feasibility wins. The
         *  smallest feasible margin gives the best objective. 0 is tried first in case quantization happens to
         *  land safe; the rest cover the ~1e-4 sine-table perturbation with headroom. */
        public double[] margins = {0.0, 1.0e-4, 3.0e-4, 6.0e-4, 1.2e-3, 2.5e-3, 5.0e-3, 1.0e-2};

        /** Robust (centered) margins, largest first: the first margin that certifies is the realized clearance
         *  on every active wall. For surrogate-objective solves (lead-in windows), where hugging walls commits
         *  fragile seam states. */
        public double[] marginsRobust = {5.0e-2, 2.0e-2, 1.0e-2, 5.0e-3, 1.2e-3, 3.0e-4, 0.0};

        public int maxInertiaPasses = 4;

        public int rungStallLimit = 2;
    }

    public static final class Result {
        public final double[] yaws;
        public final double violation;
        public final boolean feasible;
        final double[] amp;
        final double[] phase;

        Result(double[] yaws, double violation, boolean feasible) {
            this(yaws, violation, feasible, null, null);
        }

        Result(double[] yaws, double violation, boolean feasible, double[] amp, double[] phase) {
            this.yaws = yaws;
            this.violation = violation;
            this.feasible = feasible;
            this.amp = amp;
            this.phase = phase;
        }
    }

    public static double[] optimize(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        return optimize(exact, spec, feasTol, cancel, new Config());
    }

    public static double[] optimize(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                    Config cfg) {
        Result r = optimizeReturning(exact, spec, feasTol, cancel, cfg.margins, true, cfg);
        return r != null && r.feasible ? r.yaws : null;
    }

    public static double[] optimizeWithPattern(ExactJumpModel exact, JumpSpec spec, double feasTol,
                                               AtomicBoolean cancel, boolean[] zeroX, boolean[] zeroZ) {
        Config cfg = new Config();
        JumpPhysicsInputs sc = spec.asScenario();
        FacingPrefold pre = FacingPrefold.analyze(spec.constraints, new JumpLinearModel(sc));
        if (pre == null) return null;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        Result r = runLadder(exact, spec, sc, compiled, feasTol, cancel, cfg.margins, true, zeroX, zeroZ,
                System.nanoTime(), cfg, pre, 1);
        if ((r == null || !r.feasible) && JumpLinearModel.hasCrossAxis(spec.constraints)) {
            Result r2 = runLadder(exact, spec, sc, compiled, feasTol, cancel, cfg.margins, true, zeroX, zeroZ,
                    System.nanoTime(), cfg, pre, -1);
            if (r2 != null && r2.feasible) r = r2;
        }
        return r != null && r.feasible ? r.yaws : null;
    }

    /** Like {@link #optimize}, but prefers clearance over objective: the result keeps the largest
     *  certifiable uniform distance from every wall. */
    public static double[] optimizeRobust(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        Config cfg = new Config();
        Result r = optimizeReturning(exact, spec, feasTol, cancel, cfg.marginsRobust, false, cfg);
        return r != null && r.feasible ? r.yaws : null;
    }

    public static Result optimizeRobustGraded(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        Config cfg = new Config();
        return optimizeReturning(exact, spec, feasTol, cancel, cfg.marginsRobust, false, cfg);
    }

    private static final double SCAN_COARSE_STEP = 1.0;
    private static final double SCAN_ARC_STEP = 0.01;
    private static final double SCAN_ARC_TOL = 2.0e-3;
    private static final int SCAN_MAX_CANDIDATES = 240;

    private static final double ORDER_PIN_HALF_WIDTH = 1.0e-4;
    private static final double ORDER_TIE_TOL = 1.5 * YawOrderDp.H;
    private static final int ORDER_ROUNDS = 16;

    private static Result optimizeReturning(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                            double[] margins, boolean ascending, Config cfg) {
        JumpPhysicsInputs sc = spec.asScenario();
        YawOrder order = carriedOrder(spec, sc);
        if (order != null) return solveOrdered(exact, spec, sc, feasTol, cancel, margins, ascending, cfg, order);
        return solveStripped(exact, spec, sc, feasTol, cancel, margins, ascending, cfg);
    }

    private static YawOrder carriedOrder(JumpSpec spec, JumpPhysicsInputs sc) {
        if (!JumpLinearModel.hasFacingWall(spec.constraints)) return null;
        YawOrder order = YawOrder.carry(spec.constraints, YawTies.of(spec.constraints, sc.numTicks), sc.numTicks);
        return order == null || order.isEmpty() ? null : order;
    }

    private static Result solveOrdered(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc, double feasTol,
                                       AtomicBoolean cancel, double[] margins, boolean ascending, Config cfg,
                                       YawOrder order) {
        long t0 = System.nanoTime();
        JumpConstraintCompiler.Compiled full = JumpConstraintCompiler.compile(spec);
        List<JumpConstraint> base = new ArrayList<JumpConstraint>(spec.constraints.size());
        for (JumpConstraint c : spec.constraints) {
            boolean carried = false;
            for (JumpConstraint k : order.carried()) {
                if (k == c) {
                    carried = true;
                    break;
                }
            }
            if (!carried) base.add(c);
        }
        int n = sc.numTicks;
        int links = order.links().size();
        int bounds = order.bounds().size();
        boolean[] tie = new boolean[links];
        boolean[] pinLo = new boolean[bounds];
        boolean[] pinHi = new boolean[bounds];
        Result best = null;
        double bestObj = 0.0;
        Result least = null;
        int rounds = 0;
        for (int round = 0; round < ORDER_ROUNDS; round++) {
            if (cancel.get()) break;
            rounds++;
            List<JumpConstraint> active = new ArrayList<JumpConstraint>(base);
            addTies(active, order, tie, pinLo, pinHi);
            JumpSpec work = new JumpSpec(sc, active, spec.objective);
            Result r = solveStripped(exact, work, sc, feasTol, cancel, margins, ascending, cfg);
            if (r == null) break;
            double viol = violOnExact(exact, sc, full, r.yaws);
            if (least == null || viol < least.violation) least = new Result(r.yaws, viol, false);
            boolean feasible = viol <= feasTol;
            boolean[] nextTie = tie.clone();
            boolean[] nextLo = pinLo.clone();
            boolean[] nextHi = pinHi.clone();
            boolean fixedPoint = false;
            if (feasible) {
                double o = objectiveOf(exact, spec, sc, r.yaws);
                if (best == null || (spec.objective.sense == Objective.Sense.MAX ? o > bestObj : o < bestObj)) {
                    best = new Result(r.yaws, viol, true);
                    bestObj = o;
                }
            }
            if (r.amp != null) {
                YawTies ties = JumpLinearModel.hasFacingWall(active) ? YawTies.of(active, n) : null;
                double[] ordered = YawOrderDp.orderedYaws(order, ties, n, r.amp, r.phase);
                if (ordered == null) {
                    if (SolverTrace.on()) SolverTrace.log("CF", "ordered round=%d dp declined", round);
                    break;
                }
                structureOf(order, ordered, nextTie, nextLo, nextHi);
                fixedPoint = Arrays.equals(nextTie, tie) && Arrays.equals(nextLo, pinLo) && Arrays.equals(nextHi, pinHi);
            } else {
                fixedPoint = true;
            }
            if (SolverTrace.on()) {
                SolverTrace.log("CF", "ordered round=%d ties=%s viol=%.3e feasible=%s next=%s fixed=%s dp=%s fold=%s",
                        round, flags(tie), viol, feasible, flags(nextTie), fixedPoint,
                        r.amp == null ? "-" : turns(order, YawOrderDp.orderedYaws(order,
                                JumpLinearModel.hasFacingWall(active) ? YawTies.of(active, n) : null, n, r.amp, r.phase)),
                        turns(order, r.yaws));
            }
            if (fixedPoint && feasible) break;
            if (fixedPoint) {
                structureOf(order, r.yaws, nextTie, nextLo, nextHi);
                for (int l = 0; l < links; l++) nextTie[l] |= tie[l];
                for (int b = 0; b < bounds; b++) {
                    nextLo[b] |= pinLo[b];
                    nextHi[b] |= pinHi[b];
                }
                if (Arrays.equals(nextTie, tie) && Arrays.equals(nextLo, pinLo) && Arrays.equals(nextHi, pinHi)) break;
            }
            tie = nextTie;
            pinLo = nextLo;
            pinHi = nextHi;
        }
        if (SolverTrace.on()) {
            SolverTrace.log("CF", "ordered %s rounds=%d viol=%s us=%.1f",
                    best != null ? "solved" : "miss", rounds,
                    least == null ? "none" : SolverTrace.fmt("%.3e", least.violation), (System.nanoTime() - t0) / 1e3);
        }
        return best != null ? best : least;
    }

    private static String flags(boolean[] f) {
        StringBuilder sb = new StringBuilder();
        for (boolean b : f) sb.append(b ? '1' : '0');
        return sb.toString();
    }

    private static String turns(YawOrder order, double[] yaws) {
        if (yaws == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (YawOrder.Link l : order.links()) {
            if (l.laterTick() >= yaws.length) continue;
            sb.append(SolverTrace.fmt("%.3f", Angles.wrapDelta(yaws[l.hiTick] - yaws[l.loTick]))).append(' ');
        }
        return sb.toString().trim();
    }

    private static void addTies(List<JumpConstraint> active, YawOrder order, boolean[] tie, boolean[] pinLo,
                                boolean[] pinHi) {
        for (int l = 0; l < tie.length; l++) {
            if (!tie[l]) continue;
            JumpConstraint s = order.links().get(l).source;
            double at = s.rhs == 0.0 ? 0.0
                    : s.rhs + (s.cmp == JumpConstraint.Cmp.GE ? ORDER_PIN_HALF_WIDTH : -ORDER_PIN_HALF_WIDTH);
            active.add(new JumpConstraint(JumpConstraint.Mode.F, s.t1, s.t2, JumpConstraint.Op.MINUS,
                    JumpConstraint.Cmp.GE, at - ORDER_PIN_HALF_WIDTH, s.name + "eqLo"));
            active.add(new JumpConstraint(JumpConstraint.Mode.F, s.t1, s.t2, JumpConstraint.Op.MINUS,
                    JumpConstraint.Cmp.LE, at + ORDER_PIN_HALF_WIDTH, s.name + "eqHi"));
        }
        for (int b = 0; b < pinLo.length; b++) {
            if (!pinLo[b] && !pinHi[b]) continue;
            YawOrder.Bound bd = order.bounds().get(b);
            double edge = pinLo[b] ? bd.loDeg + ORDER_PIN_HALF_WIDTH : bd.hiDeg - ORDER_PIN_HALF_WIDTH;
            active.add(new JumpConstraint(JumpConstraint.Mode.F, bd.tick, null, JumpConstraint.Op.PLUS,
                    JumpConstraint.Cmp.GE, edge - ORDER_PIN_HALF_WIDTH, bd.source.name + "eqLo"));
            active.add(new JumpConstraint(JumpConstraint.Mode.F, bd.tick, null, JumpConstraint.Op.PLUS,
                    JumpConstraint.Cmp.LE, edge + ORDER_PIN_HALF_WIDTH, bd.source.name + "eqHi"));
        }
    }

    private static void structureOf(YawOrder order, double[] yaws, boolean[] tie, boolean[] pinLo, boolean[] pinHi) {
        for (int l = 0; l < tie.length; l++) {
            YawOrder.Link link = order.links().get(l);
            if (link.laterTick() >= yaws.length) continue;
            double turn = Angles.wrapDelta(yaws[link.hiTick] - yaws[link.loTick]);
            tie[l] = turn <= link.minTurnDeg + ORDER_TIE_TOL;
        }
        for (int b = 0; b < pinLo.length; b++) {
            YawOrder.Bound bd = order.bounds().get(b);
            if (bd.tick >= yaws.length) continue;
            double mid = 0.5 * (bd.loDeg + bd.hiDeg);
            double rel = mid + Angles.wrapDelta(yaws[bd.tick] - mid);
            pinLo[b] = rel <= bd.loDeg + ORDER_TIE_TOL;
            pinHi[b] = !pinLo[b] && rel >= bd.hiDeg - ORDER_TIE_TOL;
        }
    }

    private static double objectiveOf(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc, double[] yaws) {
        return spec.objective.evaluate(exact.forward(sc, sc.toGameFacings(yaws)));
    }

    private static void costates(JumpLinearModel lin, double[] cx, double[] cz, List<JumpLinearModel.Wall> walls,
                                 double[] lambda, double[] amp, double[] phase) {
        int n = lin.n;
        double[] gx = cx.clone();
        double[] gz = cz.clone();
        for (int j = 0; j < walls.size(); j++) {
            double lj = lambda[j];
            if (lj == 0.0) continue;
            JumpLinearModel.Wall w = walls.get(j);
            if (w.axis == 0) {
                for (int t = 0; t < n; t++) gx[t] -= lj * w.coef[t];
            } else if (w.axis == 1) {
                for (int t = 0; t < n; t++) gz[t] -= lj * w.coef[t];
            } else {
                if (w.coefX != null) for (int t = 0; t < n; t++) gx[t] -= lj * w.coefX[t];
                if (w.coefZ != null) for (int t = 0; t < n; t++) gz[t] -= lj * w.coefZ[t];
            }
        }
        for (int t = 0; t < n; t++) {
            amp[t] = lin.mMag(t) * StrictMath.sqrt(gx[t] * gx[t] + gz[t] * gz[t]);
            phase[t] = lin.recoverYawDeg(t, gx[t], gz[t]);
        }
    }

    private static Result solveStripped(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc, double feasTol,
                                        AtomicBoolean cancel, double[] margins, boolean ascending, Config cfg) {
        // The linear model represents only position (X/Z) walls. Facing pins and dF chains prefold into
        // it (eliminated constants / merged direction variables); any other facing wall bails.
        JumpLinearModel linA = new JumpLinearModel(sc);
        FacingPrefold pre = FacingPrefold.analyze(spec.constraints, linA);
        if (pre == null) return null;
        return solveWithPrefold(exact, spec, sc, feasTol, cancel, margins, ascending, cfg, pre);
    }

    /** Candidate anchor yaws for the open chain. Walls whose every contributing tick is pinned or in the
     *  chain have a value that is an exact sinusoid {@code A cos t + B sin t + C} of the anchor, so the
     *  feasible anchor set is localized analytically first; the ladder then runs only inside those arcs
     *  (plus the near-miss minimum). Without any chain-determined wall the whole circle is sampled
     *  coarsely. Returns null when a constant wall is violated (no anchor can fix it). */
    static double[] candidateThetas(JumpSpec spec, JumpLinearModel lin, FacingPrefold.ChainScan scan,
                                    StartBox translationBox) {
        boolean[] trivialInfeasible = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivialInfeasible);
        if (trivialInfeasible[0]) return null;
        double halfX = translationBox == null ? 0.0 : 0.5 * (translationBox.pxHi - translationBox.pxLo);
        double halfZ = translationBox == null ? 0.0 : 0.5 * (translationBox.pzHi - translationBox.pzLo);

        List<double[]> hard = new ArrayList<double[]>();
        for (JumpLinearModel.Wall w : walls) {
            double a = 0.0;
            double b = 0.0;
            double c = -w.bPrime;
            boolean chainOnly = true;
            for (int t = 0; t < lin.n; t++) {
                double k = w.coef[t];
                if (k == 0.0) continue;
                if (scan.openMember(t)) {
                    double m = scan.magnitude(t);
                    double phi = scan.phaseRad(t);
                    if (w.axis == 0) {
                        a += k * m * StrictMath.cos(phi);
                        b -= k * m * StrictMath.sin(phi);
                    } else {
                        a += k * m * StrictMath.sin(phi);
                        b += k * m * StrictMath.cos(phi);
                    }
                } else if (scan.pinnedMember(t)) {
                    c += k * scan.pinnedInput(t, w.axis);
                } else {
                    chainOnly = false;
                    break;
                }
            }
            if (!chainOnly) continue;
            double slack = Math.abs(w.p0coef) * (w.axis == 0 ? halfX : halfZ);
            if (a == 0.0 && b == 0.0) {
                boolean ok = w.eq ? Math.abs(c) <= slack : c <= slack;
                if (!ok) return null;
                continue;
            }
            hard.add(new double[]{a, b, c, w.eq ? 1.0 : 0.0, slack});
        }

        List<Double> cands = new ArrayList<Double>();
        if (hard.isEmpty()) {
            for (double th = -180.0; th < 180.0; th += SCAN_COARSE_STEP) cands.add(th);
        } else {
            double bestViol = Double.POSITIVE_INFINITY;
            double bestTh = 0.0;
            boolean inArc = false;
            double arcStart = 0.0;
            double prev = 0.0;
            for (double th = -180.0; th <= 180.0 + 1.0e-9; th += SCAN_ARC_STEP) {
                double rad = th * Math.PI / 180.0;
                double cos = StrictMath.cos(rad);
                double sin = StrictMath.sin(rad);
                double viol = 0.0;
                for (double[] h : hard) {
                    double s = h[0] * cos + h[1] * sin + h[2];
                    double v = (h[3] > 0.0 ? Math.abs(s) : Math.max(0.0, s)) - h[4];
                    if (v > viol) viol = v;
                }
                if (viol < bestViol) {
                    bestViol = viol;
                    bestTh = th;
                }
                boolean near = viol <= SCAN_ARC_TOL;
                if (near && !inArc) {
                    inArc = true;
                    arcStart = th;
                } else if (!near && inArc) {
                    inArc = false;
                    addArc(cands, arcStart, prev);
                }
                prev = th;
            }
            if (inArc) addArc(cands, arcStart, prev);
            cands.add(bestTh);
        }
        if (cands.size() > SCAN_MAX_CANDIDATES) {
            List<Double> thinned = new ArrayList<Double>(SCAN_MAX_CANDIDATES);
            for (int i = 0; i < SCAN_MAX_CANDIDATES; i++) {
                thinned.add(cands.get((int) ((long) i * cands.size() / SCAN_MAX_CANDIDATES)));
            }
            cands = thinned;
        }
        double[] out = new double[cands.size()];
        for (int i = 0; i < out.length; i++) out[i] = cands.get(i);
        return out;
    }

    private static void addArc(List<Double> cands, double from, double to) {
        double span = to - from;
        if (span <= SCAN_ARC_STEP) {
            cands.add(0.5 * (from + to));
            return;
        }
        int samples = Math.max(3, 1 + (int) Math.ceil(span / SCAN_COARSE_STEP));
        for (int i = 0; i < samples; i++) {
            cands.add(from + span * i / (samples - 1));
        }
    }

    private static Result solveWithPrefold(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc, double feasTol,
                                           AtomicBoolean cancel, double[] margins, boolean ascending, Config cfg,
                                           FacingPrefold pre) {
        long t0 = System.nanoTime();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);

        int n = sc.numTicks;
        boolean[] zeroX = null;
        boolean[] zeroZ = null;
        Result best = null;
        for (int pass = 0; pass < cfg.maxInertiaPasses; pass++) {
            if (SolverTrace.on()) {
                SolverTrace.log("CF", "pass=%d pattern=%s n=%d m=%d %s",
                        pass, SolverTrace.patternLabel(zeroX, zeroZ), n, spec.constraints.size(),
                        ascending ? "ascending" : "robust");
            }
            Result r = runLadder(exact, spec, sc, compiled, feasTol, cancel, margins, ascending, zeroX, zeroZ, t0, cfg, pre, 1);
            if ((r == null || !r.feasible) && JumpLinearModel.hasCrossAxis(spec.constraints)) {
                Result r2 = runLadder(exact, spec, sc, compiled, feasTol, cancel, margins, ascending, zeroX, zeroZ, t0, cfg, pre, -1);
                if (r2 != null && (r == null || r2.feasible || r2.violation < r.violation)) r = r2;
            }
            if (r == null) {
                if (SolverTrace.on()) SolverTrace.log("CF", "pass=%d ladder empty (trivial/unbounded/cancel)", pass);
                break;
            }
            if (r.feasible) return r;
            boolean improved = best == null || r.violation < best.violation;
            if (best == null || r.violation < best.violation) best = r;
            if (pass > 0 && !improved) {
                if (SolverTrace.on()) SolverTrace.log("CF", "pass=%d no improvement (viol=%.3e), stop", pass, r.violation);
                break;
            }

            boolean[] nzx = new boolean[n];
            boolean[] nzz = new boolean[n];
            new JumpLinearModel(sc).zeroingPattern(r.yaws, exact.inertiaThreshold(), exact.perAxisInertia(), nzx, nzz);
            if (!patternEffective(sc, nzx, nzz)) {
                if (SolverTrace.on()) SolverTrace.log("CF", "pass=%d pattern %s ineffective, stop", pass, SolverTrace.patternLabel(nzx, nzz));
                break;
            }
            if (patternEquals(zeroX, nzx) && patternEquals(zeroZ, nzz)) {
                if (SolverTrace.on()) SolverTrace.log("CF", "pass=%d pattern fixed point, stop", pass);
                break;
            }
            zeroX = nzx;
            zeroZ = nzz;
        }
        if (SolverTrace.on()) {
            SolverTrace.log("CF", "fallback bestViol=%s us=%.1f",
                    best == null ? "none" : SolverTrace.fmt("%.3e", best.violation), (System.nanoTime() - t0) / 1e3);
        }
        return best;
    }

    private static boolean patternEffective(JumpPhysicsInputs sc, boolean[] zeroX, boolean[] zeroZ) {
        for (int t = 0; t < zeroX.length; t++) {
            if (zeroX[t] && (t > 0 || sc.initialVelocity.x != 0.0)) return true;
            if (zeroZ[t] && (t > 0 || sc.initialVelocity.z != 0.0)) return true;
        }
        return false;
    }

    private static Result runLadder(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                    JumpConstraintCompiler.Compiled compiled, double feasTol, AtomicBoolean cancel,
                                    double[] margins, boolean ascending, boolean[] zeroX, boolean[] zeroZ, long t0,
                                    Config cfg, FacingPrefold pre, int dominantSign) {
        JumpLinearModel lin = new JumpLinearModel(sc, zeroX, zeroZ);
        double[] cx = new double[lin.n];
        double[] cz = new double[lin.n];
        lin.objectiveVectors(spec.objective, cx, cz);

        boolean[] trivialInfeasible = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivialInfeasible, dominantSign);
        if (trivialInfeasible[0]) return null;
        double vBound = exact.perAxisInertia() ? exact.inertiaThreshold()
                : exact.inertiaThreshold() / StrictMath.sqrt(2.0);
        walls.addAll(lin.velocityWalls(vBound));

        FacingPrefold.Reduced red = pre.reduce(cx, cz, lin.mMagAll(), walls);
        if (!pre.isIdentity() && SolverTrace.on()) {
            SolverTrace.log("CF", "prefold pinned=%d vars=%d of n=%d", pre.pinnedTicks(), red.n, lin.n);
        }
        CostateDualSolver solver = new CostateDualSolver(red.n, red.cx, red.cz, red.mMag, red.walls);

        // Each rung warm-starts from the previous margin's multipliers, so the ladder costs barely more
        // than a single solve.
        double bestViol = Double.POSITIVE_INFINITY;
        double[] bestYaws = null;
        double[] bestLambda = null;
        double[] warm = null;
        int rungStall = 0;
        for (double margin : margins) {
            if (cancel.get()) return null;
            CostateDualSolver.Result r = solver.solve(margin, warm);
            // Dual unbounded -> primal infeasible; infeasibility is monotone in the margin, so ascending
            // stops while the descending (robust) ladder keeps trying smaller rungs.
            if (r == null) {
                if (SolverTrace.on()) SolverTrace.log("CF", "rung margin=%.2e dual unbounded%s", margin, ascending ? ", ladder stop" : "");
                if (ascending) break;
                continue;
            }
            warm = r.lambda;

            double[] yaws = pre.expand(lin, spec.objective, r);
            double viol = violOnExact(exact, sc, compiled, yaws);
            if (SolverTrace.on()) {
                SolverTrace.log("CF", "rung margin=%.2e iters=%d pg=%.3e dual=%.9f viol=%.3e%s",
                        margin, solver.lastIters, solver.lastPgres, r.value, viol,
                        viol <= feasTol ? " certified" : "");
            }
            if (DEBUG) {
                double[] gf = sc.toGameFacings(yaws);
                double o = spec.objective.evaluate(exact.forward(sc, gf));
                System.out.printf("  CLOSED margin=%.2e iters=%d pg=%.3e viol=%.2e obj=%.6f%n",
                        margin, solver.lastIters, solver.lastPgres, viol, o);
            }
            if (viol < bestViol) {
                bestViol = viol;
                bestYaws = yaws;
                bestLambda = r.lambda;
                rungStall = 0;
            } else {
                rungStall++;
            }
            if (viol <= feasTol) {
                if (DEBUG) System.out.printf("  CLOSED -> %.2fus (margin=%.1e)%n", (System.nanoTime() - t0) / 1e3, margin);
                return withCostates(new Result(yaws, viol, true), lin, cx, cz, walls, r.lambda);
            }
            if (ascending && rungStall >= cfg.rungStallLimit) {
                if (SolverTrace.on()) SolverTrace.log("CF", "ladder stalled after margin=%.2e (bestViol=%.3e)", margin, bestViol);
                break;
            }
        }
        if (DEBUG) System.out.printf("  CLOSED FALLBACK %.2fus bestViol=%.2e%n",
                (System.nanoTime() - t0) / 1e3, bestViol);
        if (bestYaws == null) return null;
        return withCostates(new Result(bestYaws, bestViol, false), lin, cx, cz, walls, bestLambda);
    }

    private static Result withCostates(Result r, JumpLinearModel lin, double[] cx, double[] cz,
                                       List<JumpLinearModel.Wall> walls, double[] lambda) {
        double[] amp = new double[lin.n];
        double[] phase = new double[lin.n];
        costates(lin, cx, cz, walls, lambda, amp, phase);
        return new Result(r.yaws, r.violation, r.feasible, amp, phase);
    }

    private static boolean patternEquals(boolean[] a, boolean[] b) {
        int n = b.length;
        for (int t = 0; t < n; t++) {
            boolean av = a != null && a[t];
            if (av != b[t]) return false;
        }
        return true;
    }

    /** Weak-duality bound on the spec's objective in world coordinates: no feasible path can land beyond
     *  it. Valid even where the dual's recovery degenerates, so it certifies a primally-found solution
     *  without a search. {@code NaN} when no bound applies (facing walls, violated constant, unbounded). */
    public static double dualBound(JumpSpec spec) {
        boolean facing = JumpLinearModel.hasFacingWall(spec.constraints);
        JumpPhysicsInputs sc = spec.asScenario();
        JumpLinearModel lin = new JumpLinearModel(sc);
        FacingPrefold pre = null;
        if (facing) {
            if (!FacingPrefold.exactPins(spec.constraints)) return Double.NaN;
            pre = FacingPrefold.analyze(spec.constraints, lin);
            if (pre == null || !pre.exactLinks()) return Double.NaN;
        }
        double[] cx = new double[lin.n];
        double[] cz = new double[lin.n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivialInfeasible = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivialInfeasible);
        if (trivialInfeasible[0]) return Double.NaN;
        double value;
        if (pre != null && !pre.isIdentity()) {
            FacingPrefold.Reduced red = pre.reduce(cx, cz, lin.mMagAll(), walls);
            double pinnedTerm = pre.pinnedObjectiveTerm(cx, cz);
            if (red.n == 0) {
                value = pinnedTerm;
            } else {
                CostateDualSolver.Result r = new CostateDualSolver(red.n, red.cx, red.cz, red.mMag, red.walls).solve(0.0, null);
                if (r == null) return Double.NaN;
                value = r.value + pinnedTerm;
            }
        } else {
            CostateDualSolver.Result r = new CostateDualSolver(lin.n, cx, cz, lin.mMagAll(), walls).solve(0.0, null);
            if (r == null) return Double.NaN;
            value = r.value;
        }
        // r.value bounds max c·u with c MAX-normalized; fold the constant part back in (MIN is negated).
        double constPos;
        if (spec.objective.isCustomAngle()) {
            double rad = Angles.rad(spec.objective.customYaw);
            double dx = -StrictMath.sin(rad);
            double dz = StrictMath.cos(rad);
            if (spec.objective.isMotion()) {
                int t = spec.objective.tick;
                double c0 = lin.constPos(t, 0) - (t > 0 ? lin.constPos(t - 1, 0) : 0.0);
                double c1 = lin.constPos(t, 1) - (t > 0 ? lin.constPos(t - 1, 1) : 0.0);
                constPos = dx * c0 + dz * c1;
            } else {
                constPos = dx * lin.constPos(spec.objective.tick, 0) + dz * lin.constPos(spec.objective.tick, 1);
            }
        } else {
            int axis = spec.objective.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;
            constPos = lin.constPos(spec.objective.tick, axis);
        }
        return spec.objective.sense == Objective.Sense.MAX ? constPos + value : constPos - value;
    }

    public static double dualBoundIgnoringFacing(JumpSpec spec) {
        if (!JumpLinearModel.hasFacingWall(spec.constraints)) return dualBound(spec);
        List<JumpConstraint> kept = new ArrayList<>(spec.constraints.size());
        for (JumpConstraint c : spec.constraints) {
            if (c.mode != JumpConstraint.Mode.F) kept.add(c);
        }
        return dualBound(new JumpSpec(spec.asScenario(), kept, spec.objective));
    }

    private static double violOnExact(ExactJumpModel exact, JumpPhysicsInputs sc,
                                      JumpConstraintCompiler.Compiled compiled, double[] yawsAbsWrapped) {
        double[] gf = sc.toGameFacings(yawsAbsWrapped);
        ForwardPath path = exact.forward(sc, gf);
        return compiled.maxViolation(gf, path);
    }
}
