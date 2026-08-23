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

        /** Inward margins the recovery repair aims for, in blocks; the first that certifies wins. */
        public double[] repairMargins = {1.0e-3, 5.0e-3, 2.0e-2, 6.0e-2};

        public int rungStallLimit = 2;
    }

    public static final class Result {
        public final double[] yaws;
        public final double violation;
        public final boolean feasible;
        /** True when the certificate came from the recovery repair, which aims inward and so spends
         *  objective. A clean certificate from a later inertia pass is preferred over one of these. */
        public final boolean repaired;

        Result(double[] yaws, double violation, boolean feasible) {
            this(yaws, violation, feasible, false);
        }

        Result(double[] yaws, double violation, boolean feasible, boolean repaired) {
            this.yaws = yaws;
            this.violation = violation;
            this.feasible = feasible;
            this.repaired = repaired;
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

    private static final double RAD_CF = Math.PI / 180.0;
    private static final int REPAIR_ITERS = 400;
    static final boolean REPAIR_ON = !"0".equals(System.getProperty("pkc.cf.repair", "1"));
    private static final double REPAIR_METRIC_EPS = 5.0e-3;

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
        List<double[]> recoveries = REPAIR_ON ? new ArrayList<double[]>() : null;
        for (int pass = 0; pass < cfg.maxInertiaPasses; pass++) {
            if (SolverTrace.on()) {
                SolverTrace.log("CF", "pass=%d pattern=%s n=%d m=%d %s",
                        pass, SolverTrace.patternLabel(zeroX, zeroZ), n, spec.constraints.size(),
                        ascending ? "ascending" : "robust");
            }
            Result r = runLadder(exact, spec, sc, compiled, feasTol, cancel, margins, ascending, zeroX, zeroZ, t0, cfg, pre, recoveries);
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
        if (REPAIR_ON && best != null && !best.feasible && recoveries != null) {
            Result rep = repairRecovery(exact, spec, sc, compiled, feasTol, cancel, best, cfg, recoveries);
            if (rep != null) return rep;
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

    /** The dual recovers each tick as u_t = m_t g_t/‖g_t‖, which carries no direction once the costate
     *  vanishes, and at the optimum it vanishes on nearly every tick because c = A^T lambda there. The
     *  information about which point of the optimal face to take lives in the walls, not the costate,
     *  so solve them: damped Gauss-Newton on the violated walls, measuring the correction in second
     *  differences so a repair cannot flick the facing path it is fixing. */
    private static double[] repairOnLinear(JumpLinearModel lin, List<JumpLinearModel.Wall> walls,
                                           double[] yaws, double margin, AtomicBoolean cancel) {
        int n = lin.n;
        if (n < 3 || walls.isEmpty()) return null;
        double[] y = yaws.clone();
        double[][] metric = secondDifferenceMetric(n);
        double best = worstWall(lin, walls, y);
        if (best <= -margin) return null;
        for (int it = 0; it < REPAIR_ITERS; it++) {
            if (cancel != null && cancel.get()) return null;
            List<Integer> bad = new ArrayList<Integer>();
            for (int j = 0; j < walls.size(); j++) {
                if (wallAt(lin, walls.get(j), y) - walls.get(j).bPrime > -margin) bad.add(j);
            }
            if (bad.isEmpty()) return y;
            int m = bad.size();
            double[][] jac = new double[m][n];
            double[] want = new double[m];
            for (int i = 0; i < m; i++) {
                JumpLinearModel.Wall w = walls.get(bad.get(i));
                wallGradAt(lin, w, y, jac[i]);
                want[i] = (w.bPrime - margin) - wallAt(lin, w, y);
            }
            double[][] jw = new double[m][n];
            for (int a = 0; a < m; a++) {
                for (int x = 0; x < n; x++) {
                    double sum = 0.0;
                    for (int z = 0; z < n; z++) sum += jac[a][z] * metric[z][x];
                    jw[a][x] = sum;
                }
            }
            double[][] gram = new double[m][m];
            for (int a = 0; a < m; a++) {
                for (int b = 0; b < m; b++) {
                    double sum = 0.0;
                    for (int x = 0; x < n; x++) sum += jw[a][x] * jac[b][x];
                    gram[a][b] = sum;
                }
            }
            double scale = 0.0;
            for (int a = 0; a < m; a++) scale = Math.max(scale, Math.abs(gram[a][a]));
            double[] lam = solveDense(gram, want, scale * 1.0e-10 + 1.0e-20);
            boolean moved = false;
            for (double damp = 1.0; damp >= 1.0 / 512.0; damp *= 0.5) {
                double[] c = y.clone();
                for (int a = 0; a < m; a++) {
                    if (lam[a] == 0.0) continue;
                    for (int t = 0; t < n; t++) c[t] += damp * lam[a] * jw[a][t];
                }
                double v = worstWall(lin, walls, c);
                if (v < best - 1.0e-15) {
                    y = c;
                    best = v;
                    moved = true;
                    break;
                }
            }
            if (!moved) break;
        }
        return best < worstWall(lin, walls, yaws) ? y : null;
    }

    private static double wallAt(JumpLinearModel lin, JumpLinearModel.Wall w, double[] yaws) {
        double sum = 0.0;
        for (int t = 0; t < lin.n; t++) {
            if (w.coef[t] == 0.0) continue;
            double phi = lin.baseArg(t) + yaws[t] * RAD_CF;
            sum += w.coef[t] * lin.mMag(t) * (w.axis == 0 ? Math.cos(phi) : Math.sin(phi));
        }
        return sum;
    }

    private static void wallGradAt(JumpLinearModel lin, JumpLinearModel.Wall w, double[] yaws, double[] out) {
        for (int t = 0; t < lin.n; t++) {
            if (w.coef[t] == 0.0) {
                out[t] = 0.0;
                continue;
            }
            double phi = lin.baseArg(t) + yaws[t] * RAD_CF;
            out[t] = w.coef[t] * lin.mMag(t) * (w.axis == 0 ? -Math.sin(phi) : Math.cos(phi)) * RAD_CF;
        }
    }

    private static double worstWall(JumpLinearModel lin, List<JumpLinearModel.Wall> walls, double[] yaws) {
        double worst = Double.NEGATIVE_INFINITY;
        for (JumpLinearModel.Wall w : walls) worst = Math.max(worst, wallAt(lin, w, yaws) - w.bPrime);
        return worst;
    }

    private static double[][] secondDifferenceMetric(int n) {
        double[][] a = new double[n][n];
        double[] cf = {1.0, -2.0, 1.0};
        for (int t = 1; t < n - 1; t++) {
            int[] idx = {t - 1, t, t + 1};
            for (int i = 0; i < 3; i++) {
                for (int k = 0; k < 3; k++) a[idx[i]][idx[k]] += cf[i] * cf[k];
            }
        }
        for (int i = 0; i < n; i++) a[i][i] += REPAIR_METRIC_EPS;
        return invertDense(a);
    }

    private static double[][] invertDense(double[][] a) {
        int n = a.length;
        double[][] w = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, w[i], 0, n);
            w[i][n + i] = 1.0;
        }
        for (int c = 0; c < n; c++) {
            int piv = c;
            for (int r = c + 1; r < n; r++) if (Math.abs(w[r][c]) > Math.abs(w[piv][c])) piv = r;
            double[] tmp = w[c];
            w[c] = w[piv];
            w[piv] = tmp;
            double d = w[c][c];
            if (Math.abs(d) < 1.0e-16) d = d >= 0.0 ? 1.0e-16 : -1.0e-16;
            for (int k = 0; k < 2 * n; k++) w[c][k] /= d;
            for (int r = 0; r < n; r++) {
                if (r == c) continue;
                double f = w[r][c];
                if (f == 0.0) continue;
                for (int k = 0; k < 2 * n; k++) w[r][k] -= f * w[c][k];
            }
        }
        double[][] out = new double[n][n];
        for (int i = 0; i < n; i++) System.arraycopy(w[i], n, out[i], 0, n);
        return out;
    }

    private static double[] solveDense(double[][] a, double[] b, double reg) {
        int m = b.length;
        double[][] w = new double[m][m + 1];
        for (int i = 0; i < m; i++) {
            System.arraycopy(a[i], 0, w[i], 0, m);
            w[i][i] += reg;
            w[i][m] = b[i];
        }
        for (int c = 0; c < m; c++) {
            int piv = c;
            for (int r = c + 1; r < m; r++) if (Math.abs(w[r][c]) > Math.abs(w[piv][c])) piv = r;
            double[] tmp = w[c];
            w[c] = w[piv];
            w[piv] = tmp;
            if (Math.abs(w[c][c]) < 1.0e-16) continue;
            for (int r = 0; r < m; r++) {
                if (r == c) continue;
                double f = w[r][c] / w[c][c];
                if (f == 0.0) continue;
                for (int k = c; k <= m; k++) w[r][k] -= f * w[c][k];
            }
        }
        double[] x = new double[m];
        for (int i = 0; i < m; i++) x[i] = Math.abs(w[i][i]) < 1.0e-16 ? 0.0 : w[i][m] / w[i][i];
        return x;
    }

    private static Result runLadder(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                    JumpConstraintCompiler.Compiled compiled, double feasTol, AtomicBoolean cancel,
                                    double[] margins, boolean ascending, boolean[] zeroX, boolean[] zeroZ, long t0,
                                    Config cfg, FacingPrefold pre) {
        return runLadder(exact, spec, sc, compiled, feasTol, cancel, margins, ascending, zeroX, zeroZ, t0,
                cfg, pre, null);
    }

    private static Result runLadder(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                    JumpConstraintCompiler.Compiled compiled, double feasTol, AtomicBoolean cancel,
                                    double[] margins, boolean ascending, boolean[] zeroX, boolean[] zeroZ, long t0,
                                    Config cfg, FacingPrefold pre, List<double[]> rungRecoveries) {
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
            if (rungRecoveries != null) rungRecoveries.add(yaws.clone());
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

    /** The dual recovers each tick as u_t = m_t g_t over the costate norm, which carries no direction
     *  once the costate vanishes, and at the optimum it vanishes on nearly every tick because c equals
     *  A transpose lambda there. The information about which point of the optimal face to take lives in
     *  the walls, not the costate, so solve them. Runs only after every margin rung and every inertia
     *  pass has failed, so a clean certificate always wins: this one aims inward and spends objective. */
    private static Result repairRecovery(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                         JumpConstraintCompiler.Compiled compiled, double feasTol,
                                         AtomicBoolean cancel, Result best, Config cfg,
                                         List<double[]> recoveries) {
        int n = sc.numTicks;
        boolean[] zx = new boolean[n];
        boolean[] zz = new boolean[n];
        new JumpLinearModel(sc).zeroingPattern(best.yaws, exact.inertiaThreshold(), exact.perAxisInertia(), zx, zz);
        JumpLinearModel lin = new JumpLinearModel(sc, zx, zz);
        boolean[] trivialInfeasible = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivialInfeasible);
        if (trivialInfeasible[0]) return null;
        double vBound = exact.perAxisInertia() ? exact.inertiaThreshold()
                : exact.inertiaThreshold() / Math.sqrt(2.0);
        walls.addAll(lin.velocityWalls(vBound));

        double[] fromDual = best.yaws;
        double[] cur = best.yaws;
        double viol = best.violation;
        for (double rm : cfg.repairMargins) {
            for (int src = 0; src < 2 && viol > feasTol; src++) {
                if (cancel.get()) return null;
                double[] repaired = repairOnLinear(lin, walls, src == 0 ? fromDual : cur, rm, cancel);
                if (repaired == null) continue;
                double rv = violOnExact(exact, sc, compiled, repaired);
                if (SolverTrace.on()) {
                    SolverTrace.log("CF", "recovery repair rm=%.2e from=%s viol %.3e -> %.3e%s",
                            rm, src == 0 ? "dual" : "best", viol, rv, rv <= feasTol ? " certified" : "");
                }
                if (rv < viol) {
                    cur = repaired;
                    viol = rv;
                }
            }
            if (viol <= feasTol) break;
        }
        return viol <= feasTol ? new Result(cur, viol, true, true) : null;
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
