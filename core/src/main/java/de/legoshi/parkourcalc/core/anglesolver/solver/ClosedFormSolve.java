package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
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

        Result(double[] yaws, double violation, boolean feasible) {
            this.yaws = yaws;
            this.violation = violation;
            this.feasible = feasible;
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

    public static double[] recoverFace(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                       double[] seed, long deadlineNanos, boolean[] frozen) {
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        SmoothFaceRecovery.Config fc = new SmoothFaceRecovery.Config();
        fc.deadlineNanos = deadlineNanos;
        fc.frozen = frozen;
        return SmoothFaceRecovery.smooth(exact, spec, compiled, seed, feasTol, cancel, fc);
    }

    public static double[] optimizeWithPattern(ExactJumpModel exact, JumpSpec spec, double feasTol,
                                               AtomicBoolean cancel, boolean[] zeroX, boolean[] zeroZ) {
        Config cfg = new Config();
        JumpPhysicsInputs sc = spec.asScenario();
        FacingPrefold pre = FacingPrefold.analyze(spec.constraints, new JumpLinearModel(sc));
        if (pre == null) return null;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        Result r = runLadder(exact, spec, sc, compiled, feasTol, cancel, cfg.margins, true, zeroX, zeroZ,
                System.nanoTime(), cfg, pre);
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
    private static final double[] SCAN_REFINE_STEPS = {0.1, 0.01, 0.001};
    private static final double SCAN_ARC_STEP = 0.01;
    private static final double SCAN_ARC_TOL = 2.0e-3;
    private static final int SCAN_MAX_CANDIDATES = 240;

    private static Result optimizeReturning(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                            double[] margins, boolean ascending, Config cfg) {
        JumpPhysicsInputs sc = spec.asScenario();

        // The linear model represents only position (X/Z) walls. Facing pins and dF chains prefold into
        // it (eliminated constants / merged direction variables); a single free chain the merge cannot
        // take (key-combo changes or turn offsets) is scanned over its anchor yaw instead; any other
        // facing wall bails.
        JumpLinearModel linA = new JumpLinearModel(sc);
        FacingPrefold pre = FacingPrefold.analyze(spec.constraints, linA);
        if (pre == null) {
            FacingPrefold.ChainScan scan = FacingPrefold.scannable(spec.constraints, linA);
            if (scan == null) return null;
            return scanChain(exact, spec, sc, feasTol, cancel, margins, ascending, cfg, scan);
        }
        return solveWithPrefold(exact, spec, sc, feasTol, cancel, margins, ascending, cfg, pre);
    }

    private static Result scanChain(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc, double feasTol,
                                    AtomicBoolean cancel, double[] margins, boolean ascending, Config cfg,
                                    FacingPrefold.ChainScan scan) {
        long t0 = System.nanoTime();
        double[] thetas = candidateThetas(spec, new JumpLinearModel(sc), scan, null);
        if (thetas == null) {
            if (SolverTrace.on()) SolverTrace.log("CF", "chain scan trivially infeasible");
            return null;
        }
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        Result bestFeas = null;
        double bestScore = 0.0;
        double bestTheta = Double.NaN;
        Result bestInfeas = null;
        for (double th : thetas) {
            if (cancel.get()) break;
            Result r = solveWithPrefold(exact, spec, sc, feasTol, cancel, margins, ascending, cfg, scan.at(th));
            if (r == null) continue;
            if (!r.feasible) {
                if (bestInfeas == null || r.violation < bestInfeas.violation) bestInfeas = r;
                continue;
            }
            double score = scanScore(exact, spec, sc, r.yaws);
            if (bestFeas == null || (max ? score > bestScore : score < bestScore)) {
                bestFeas = r;
                bestScore = score;
                bestTheta = th;
            }
        }
        if (bestFeas != null) {
            for (double step : SCAN_REFINE_STEPS) {
                double center = bestTheta;
                for (int i = -9; i <= 9 && !cancel.get(); i++) {
                    if (i == 0) continue;
                    double th = center + i * step;
                    Result r = solveWithPrefold(exact, spec, sc, feasTol, cancel, margins, ascending, cfg, scan.at(th));
                    if (r == null || !r.feasible) continue;
                    double score = scanScore(exact, spec, sc, r.yaws);
                    if (max ? score > bestScore : score < bestScore) {
                        bestFeas = r;
                        bestScore = score;
                        bestTheta = th;
                    }
                }
            }
        }
        if (SolverTrace.on()) {
            SolverTrace.log("CF", "chain scan %s theta=%s cands=%d ms=%.1f",
                    bestFeas != null ? "solved" : "miss",
                    bestFeas != null ? SolverTrace.fmt("%.4f", bestTheta) : "-",
                    thetas.length, (System.nanoTime() - t0) / 1e6);
        }
        return bestFeas != null ? bestFeas : bestInfeas;
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
                        a += k * m * Math.cos(phi);
                        b -= k * m * Math.sin(phi);
                    } else {
                        a += k * m * Math.sin(phi);
                        b += k * m * Math.cos(phi);
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
                double cos = Math.cos(rad);
                double sin = Math.sin(rad);
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

    private static double scanScore(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc, double[] yaws) {
        double o = exact.forward(sc, sc.toGameFacings(yaws)).getPos(spec.objective.tick, spec.objective.axis);
        return spec.objective.scored(o, sc.startYaw, yaws);
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
            Result r = runLadder(exact, spec, sc, compiled, feasTol, cancel, margins, ascending, zeroX, zeroZ, t0, cfg, pre);
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
                                    Config cfg, FacingPrefold pre) {
        JumpLinearModel lin = new JumpLinearModel(sc, zeroX, zeroZ);
        double[] cx = new double[lin.n];
        double[] cz = new double[lin.n];
        lin.objectiveVectors(spec.objective, cx, cz);

        // Compile the wall structure once (margin applied inside the dual solve); a violated constant
        // constraint is unfixable, so bail to the fallback immediately.
        boolean[] trivialInfeasible = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivialInfeasible);
        if (trivialInfeasible[0]) return null;
        double vBound = exact.perAxisInertia() ? exact.inertiaThreshold()
                : exact.inertiaThreshold() / Math.sqrt(2.0);
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
                double o = exact.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
                System.out.printf("  CLOSED margin=%.2e iters=%d pg=%.3e viol=%.2e obj=%.6f%n",
                        margin, solver.lastIters, solver.lastPgres, viol, o);
            }
            if (viol < bestViol) {
                bestViol = viol;
                bestYaws = yaws;
                rungStall = 0;
            } else {
                rungStall++;
            }
            if (viol <= feasTol) {
                if (DEBUG) System.out.printf("  CLOSED -> %.2fus (margin=%.1e)%n", (System.nanoTime() - t0) / 1e3, margin);
                return new Result(yaws, viol, true);
            }
            if (ascending && rungStall >= cfg.rungStallLimit) {
                if (SolverTrace.on()) SolverTrace.log("CF", "ladder stalled after margin=%.2e (bestViol=%.3e)", margin, bestViol);
                break;
            }
        }
        if (DEBUG) System.out.printf("  CLOSED FALLBACK %.2fus bestViol=%.2e%n",
                (System.nanoTime() - t0) / 1e3, bestViol);
        if (bestYaws == null) return null;
        return new Result(bestYaws, bestViol, false);
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
        if (JumpLinearModel.hasFacingWall(spec.constraints)) return Double.NaN;
        JumpPhysicsInputs sc = spec.asScenario();
        JumpLinearModel lin = new JumpLinearModel(sc);
        double[] cx = new double[lin.n];
        double[] cz = new double[lin.n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivialInfeasible = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivialInfeasible);
        if (trivialInfeasible[0]) return Double.NaN;
        CostateDualSolver.Result r = new CostateDualSolver(lin.n, cx, cz, lin.mMagAll(), walls).solve(0.0, null);
        if (r == null) return Double.NaN;
        // r.value bounds max c·u with c MAX-normalized; fold the constant part back in (MIN is negated).
        int axis = spec.objective.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;
        double constPos = lin.constPos(spec.objective.tick, axis);
        return spec.objective.sense == Objective.Sense.MAX ? constPos + r.value : constPos - r.value;
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
