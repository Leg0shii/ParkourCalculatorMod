package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GateMip {

    public static boolean DEBUG = false;

    private static final double RAD = Math.PI / 180.0;
    private static final double BAND_LO_FACTOR = 0.25;
    private static final double BAND_HI_FACTOR = 4.0;
    private static final int MAX_CRITICAL = 8;
    private static final int MAX_PATTERNS = 32;
    private static final int MAX_KEEP_ALIVE = 4;
    private static final double RESTORE_AIM = 1.0e-4;
    private static final double RESTORE_STEP_CAP_DEG = 20.0;
    private static final int RESTORE_ITERS = 250;
    private static final int SLP_PHASE1 = 160;
    private static final int SLP_TOTAL = 220;
    private static final double BOUND_TOL = 1.0e-4;
    private static final long TREE_MIN_BUDGET_NANOS = 3_000_000_000L;
    private static final int MAX_COMPLETIONS = 6;
    private static final double COMPLETION_SHARE = 0.25;

    private GateMip() {
    }

    public static final class Result {
        public final double[] yaws;
        public final double objective;
        public final double normed;
        public final boolean feasible;
        public final boolean certifiedInfeasible;
        public final double bound;
        public final String certificate;
        public final int patternsTried;
        public final int patternsInfeasible;

        Result(double[] yaws, double objective, double normed, boolean feasible, boolean certifiedInfeasible,
               double bound, String certificate, int patternsTried, int patternsInfeasible) {
            this.yaws = yaws;
            this.objective = objective;
            this.normed = normed;
            this.feasible = feasible;
            this.certifiedInfeasible = certifiedInfeasible;
            this.bound = bound;
            this.certificate = certificate;
            this.patternsTried = patternsTried;
            this.patternsInfeasible = patternsInfeasible;
        }
    }

    public static double[] improve(ExactJumpModel exact, JumpSpec spec, double[] baseline, double feasTol,
                                   AtomicBoolean cancel, long deadlineNanos) {
        Result r = solve(exact, spec, feasTol, cancel, deadlineNanos, baseline);
        if (r.feasible && r.yaws != null) {
            if (baseline == null) return r.yaws;
            boolean max = spec.objective.sense == Objective.Sense.MAX;
            JumpPhysicsInputs sc = spec.asScenario();
            double baseObj = objectiveOf(exact, sc, spec, Angles.wrapAll(baseline));
            if (better(r.objective, baseObj, max)) return r.yaws;
        }
        return baseline;
    }

    public static Result solve(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                               long deadlineNanos) {
        return solve(exact, spec, feasTol, cancel, deadlineNanos, null);
    }

    public static Result solve(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                               long deadlineNanos, double[] seedBaseline) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        int axisIdx = spec.objective.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;

        JumpLinearModel free = new JumpLinearModel(sc);
        if (JumpLinearModel.hasFacingWall(spec.constraints) && FacingPrefold.analyze(spec.constraints, free) == null) {
            return new Result(null, Double.NaN, Double.NEGATIVE_INFINITY, false, false, Double.NaN,
                    "facing walls unfoldable (dF is P5)", 0, 0);
        }
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> freeWalls = free.compileWalls(spec.constraints, 0.0, trivial);
        if (trivial[0]) {
            return new Result(null, Double.NaN, Double.NEGATIVE_INFINITY, false, true, Double.NaN,
                    "a position constant constraint is violated (trivially infeasible before any gate)", 0, 0);
        }

        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double thr = exact.inertiaThreshold();

        double[] reference = referenceTrajectory(exact, spec, sc, free, seedBaseline, feasTol, cancel);
        List<Pattern> patterns = enumeratePatterns(spec, sc, free, freeWalls, exact, reference, axisIdx, max, thr);

        boolean freeFeasible = false;
        for (Pattern p : patterns) {
            if ("free".equals(p.label)) { freeFeasible = p.diskFeasible; break; }
        }
        double certBound = tightBound(exact, spec, sc, free, reference, patterns, freeFeasible, axisIdx, max, thr);

        int tried = patterns.size();
        int infeasible = 0;
        for (Pattern p : patterns) if (!p.diskFeasible) infeasible++;

        long budget = remaining(deadlineNanos);
        long completionDeadline = deadlineNanos == 0L ? 0L
                : System.nanoTime() + (long) (budget * COMPLETION_SHARE);

        double bestNormed = Double.NEGATIVE_INFINITY;
        double[] bestYaws = null;
        int completions = 0;
        for (Pattern p : patterns) {
            if (!p.diskFeasible) continue;
            if (completions >= MAX_COMPLETIONS || expired(cancel, completionDeadline)) break;
            if (p.bound <= bestNormed + BOUND_TOL) continue;
            completions++;
            double[] cand = complete(exact, spec, sc, free, p, reference, feasTol, cancel);
            if (cand == null) continue;
            double viol = violationOf(exact, sc, compiled, cand);
            if (viol > feasTol) continue;
            double normed = normedObjective(exact, sc, spec, cand, max);
            if (normed > bestNormed) {
                bestNormed = normed;
                bestYaws = cand;
            }
        }

        if (bestYaws == null || bestNormed < certBound - BOUND_TOL) {
            if (remaining(deadlineNanos) > TREE_MIN_BUDGET_NANOS) {
                if (DEBUG) System.out.printf("  GATE tree cold-miss: fast=%.9f cert=%.9f treeBudgetMs=%d%n",
                        bestNormed, certBound, remaining(deadlineNanos) / 1_000_000L);
                double[] tree = treeCompletion(exact, spec, feasTol, cancel, deadlineNanos, bestYaws);
                if (tree != null && violationOf(exact, sc, compiled, tree) <= feasTol) {
                    double normed = normedObjective(exact, sc, spec, tree, max);
                    if (DEBUG) System.out.printf("  GATE tree result normed=%.9f%n", normed);
                    if (normed > bestNormed) {
                        bestNormed = normed;
                        bestYaws = tree;
                    }
                }
            }
        }

        if (bestYaws != null) {
            double obj = objectiveOf(exact, sc, spec, bestYaws);
            if (DEBUG) System.out.printf("  GATE feasible normed=%.9f bound=%.9f patterns=%d completions=%d%n",
                    bestNormed, certBound, tried, completions);
            return new Result(bestYaws, obj, bestNormed, true, false,
                    Double.isInfinite(certBound) ? Double.NaN : certBound,
                    "feasible; disk-kernel gate bound certifies optimality gap", tried, infeasible);
        }

        boolean allInfeasible = tried > 0 && infeasible == tried
                && crossCheckInfeasible(spec, free, freeWalls);
        if (allInfeasible) {
            if (DEBUG) System.out.printf("  GATE CERTIFIED INFEASIBLE over %d gate assignments%n", tried);
            return new Result(null, Double.NaN, Double.NEGATIVE_INFINITY, false, true, Double.NaN,
                    "every reachable gate assignment's disk relaxation is primal-infeasible (" + tried
                            + " assignments); the relaxation is looser than the byte-exact gate, so the gate "
                            + "problem is byte-exact infeasible", tried, infeasible);
        }
        return new Result(null, Double.NaN, Double.NEGATIVE_INFINITY, false, false,
                Double.isInfinite(certBound) ? Double.NaN : certBound,
                "no byte-exact gate completion found (relaxation feasible: not certified infeasible)", tried, infeasible);
    }

    private static double tightBound(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                     JumpLinearModel free, double[] reference, List<Pattern> patterns,
                                     boolean freeFeasible, int axisIdx, boolean max, double thr) {
        double maxOverFeasible = Double.NEGATIVE_INFINITY;
        double freeBound = Double.NEGATIVE_INFINITY;
        for (Pattern p : patterns) {
            if (!p.diskFeasible) continue;
            if ("free".equals(p.label)) freeBound = p.bound;
            else if (p.bound > maxOverFeasible) maxOverFeasible = p.bound;
        }
        if (!freeFeasible) return maxOverFeasible;
        if (reference == null) return freeBound;
        int tstar = dominantBranchTick(exact, sc, reference, axisIdx, thr);
        if (tstar < 0) return freeBound;

        int n = free.n;
        boolean[] zx = new boolean[n];
        boolean[] zz = new boolean[n];
        if (axisIdx == 0) zx[tstar] = true; else zz[tstar] = true;
        Pattern in = bound(spec, sc, "cellIN", zx, zz, null, axisIdx, max, thr);
        JumpLinearModel.Wall kaP = free.keepAliveWall(axisIdx, tstar, thr, true);
        JumpLinearModel.Wall kaM = free.keepAliveWall(axisIdx, tstar, thr, false);
        if (in == null || kaP == null || kaM == null) return freeBound;
        Pattern outP = bound(spec, sc, "cellOUT+", null, null, kaP, axisIdx, max, thr);
        Pattern outM = bound(spec, sc, "cellOUT-", null, null, kaM, axisIdx, max, thr);
        double cb = Double.NEGATIVE_INFINITY;
        if (in.diskFeasible) cb = Math.max(cb, in.bound);
        if (outP != null && outP.diskFeasible) cb = Math.max(cb, outP.bound);
        if (outM != null && outM.diskFeasible) cb = Math.max(cb, outM.bound);
        if (Double.isInfinite(cb)) return freeBound;
        return Math.min(cb, freeBound);
    }

    private static int dominantBranchTick(ExactJumpModel exact, JumpPhysicsInputs sc, double[] reference,
                                          int axisIdx, double thr) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(reference));
        ForwardPath path = exact.forward(sc, gf);
        double[] v = axisIdx == 0 ? path.velX : path.velZ;
        if (v == null) return -1;
        int n = sc.numTicks;
        int reversal = -1;
        int nearest = -1;
        double nearestGap = Double.MAX_VALUE;
        double lo = BAND_LO_FACTOR * thr;
        double hi = BAND_HI_FACTOR * thr;
        for (int k = 1; k < n; k++) {
            if (v[k - 1] * v[k] < 0.0) reversal = k;
            double av = Math.abs(v[k]);
            if (av >= lo && av <= hi) {
                double gap = Math.abs(av - thr);
                if (gap < nearestGap) { nearestGap = gap; nearest = k; }
            }
        }
        return reversal >= 0 ? reversal : nearest;
    }

    private static final class Pattern {
        final String label;
        final boolean[] zeroX;
        final boolean[] zeroZ;
        final JumpLinearModel.Wall keepAlive;
        final double bound;
        final boolean diskFeasible;
        final double[] diskGx;
        final double[] diskGz;

        Pattern(String label, boolean[] zeroX, boolean[] zeroZ, JumpLinearModel.Wall keepAlive,
                double bound, boolean diskFeasible, double[] diskGx, double[] diskGz) {
            this.label = label;
            this.zeroX = zeroX;
            this.zeroZ = zeroZ;
            this.keepAlive = keepAlive;
            this.bound = bound;
            this.diskFeasible = diskFeasible;
            this.diskGx = diskGx;
            this.diskGz = diskGz;
        }
    }

    private static List<Pattern> enumeratePatterns(JumpSpec spec, JumpPhysicsInputs sc, JumpLinearModel free,
                                                   List<JumpLinearModel.Wall> freeWalls, ExactJumpModel exact,
                                                   double[] reference, int axisIdx, boolean max, double thr) {
        int n = sc.numTicks;
        List<Pattern> out = new ArrayList<>();

        Pattern freePat = bound(spec, sc, "free", null, null, null, axisIdx, max, thr);
        if (freePat != null) out.add(freePat);

        boolean[] critical = bandCritical(exact, sc, free, reference, thr, n);
        List<Pattern> cands = new ArrayList<>();
        for (int a = 0; a < 2; a++) {
            for (int k = 1; k < n; k++) {
                if (!critical[a * n + k]) continue;
                boolean[] zx = new boolean[n];
                boolean[] zz = new boolean[n];
                if (a == 0) zx[k] = true; else zz[k] = true;
                Pattern p = bound(spec, sc, (a == 0 ? "zx1@" : "zz1@") + k, zx, zz, null, axisIdx, max, thr);
                if (p != null) cands.add(p);
                if (k < n - 1) {
                    boolean[] sx = new boolean[n];
                    boolean[] sz = new boolean[n];
                    for (int t = k; t < n; t++) { if (a == 0) sx[t] = true; else sz[t] = true; }
                    Pattern ps = bound(spec, sc, (a == 0 ? "zx@" : "zz@") + k, sx, sz, null, axisIdx, max, thr);
                    if (ps != null) cands.add(ps);
                }
            }
        }
        cands.addAll(keepAlivePatterns(spec, sc, free, exact, reference, axisIdx, max, thr));

        cands.sort((x, y) -> Double.compare(y.bound, x.bound));
        for (int i = 0; i < cands.size() && out.size() < MAX_PATTERNS; i++) out.add(cands.get(i));
        out.sort((x, y) -> Double.compare(y.bound, x.bound));
        return out;
    }

    private static boolean[] bandCritical(ExactJumpModel exact, JumpPhysicsInputs sc, JumpLinearModel free,
                                          double[] reference, double thr, int n) {
        boolean[] crit = new boolean[2 * n];
        double lo = BAND_LO_FACTOR * thr;
        double hi = BAND_HI_FACTOR * thr;
        if (reference != null) {
            double[] gf = sc.toGameFacings(Angles.wrapAll(reference));
            ForwardPath path = exact.forward(sc, gf);
            for (int a = 0; a < 2; a++) {
                double[] v = a == 0 ? path.velX : path.velZ;
                int count = 0;
                for (int k = 1; k < n && count < MAX_CRITICAL; k++) {
                    double av = Math.abs(v[k]);
                    boolean band = av >= lo && av <= hi;
                    boolean flip = v[k - 1] * v[k] < 0.0;
                    if (band || flip) { crit[a * n + k] = true; count++; }
                }
            }
            return crit;
        }
        for (int a = 0; a < 2; a++) {
            int count = 0;
            for (int k = 1; k < n && count < MAX_CRITICAL; k++) { crit[a * n + k] = true; count++; }
        }
        return crit;
    }

    private static List<Pattern> keepAlivePatterns(JumpSpec spec, JumpPhysicsInputs sc, JumpLinearModel free,
                                                   ExactJumpModel exact, double[] reference, int axisIdx,
                                                   boolean max, double thr) {
        List<Pattern> out = new ArrayList<>();
        if (reference == null) return out;
        double[] gf = sc.toGameFacings(Angles.wrapAll(reference));
        ForwardPath path = exact.forward(sc, gf);
        double[] v = axisIdx == 0 ? path.velX : path.velZ;
        if (v == null) return out;
        for (int k = 1; k < free.n && out.size() < MAX_KEEP_ALIVE; k++) {
            if (Math.abs(v[k]) >= thr && v[k - 1] * v[k] >= 0.0) continue;
            boolean positive = max;
            JumpLinearModel.Wall w = free.keepAliveWall(axisIdx, k, thr, positive);
            if (w == null) continue;
            Pattern p = bound(spec, sc, w.name, null, null, w, axisIdx, max, thr);
            if (p != null) out.add(p);
        }
        return out;
    }

    private static Pattern bound(JumpSpec spec, JumpPhysicsInputs sc, String label, boolean[] zeroX,
                                 boolean[] zeroZ, JumpLinearModel.Wall keepAlive, int axisIdx, boolean max,
                                 double thr) {
        JumpLinearModel lin = (zeroX == null && zeroZ == null) ? new JumpLinearModel(sc)
                : new JumpLinearModel(sc, zeroX, zeroZ);
        int n = lin.n;
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivial);
        if (trivial[0]) return null;
        if (zeroX != null || zeroZ != null) {
            List<JumpLinearModel.Wall> vel = lin.velocityWalls(thr);
            if (vel.isEmpty()) return null;
            walls.addAll(vel);
        }
        if (keepAlive != null) walls.add(keepAlive);
        double[] cx = new double[n];
        double[] cz = new double[n];
        lin.objectiveVectors(spec.objective, cx, cz);
        DiskSocpKernel.Result disk = DiskSocpKernel.solve(n, cx, cz, lin.mMagAll(), walls);
        double cp = lin.constPos(spec.objective.tick, axisIdx);
        if (disk == null || !disk.converged) {
            return new Pattern(label, zeroX, zeroZ, keepAlive, Double.NEGATIVE_INFINITY, false, null, null);
        }
        double bound = disk.value + (max ? cp : -cp);
        return new Pattern(label, zeroX, zeroZ, keepAlive, bound, true, disk.gx, disk.gz);
    }

    private static double[] complete(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                     JumpLinearModel free, Pattern p, double[] reference, double feasTol,
                                     AtomicBoolean cancel) {
        if (p.zeroX != null || p.zeroZ != null) {
            double[] y = ClosedFormSolve.optimizeWithPattern(exact, spec, feasTol, cancel, p.zeroX, p.zeroZ);
            if (y != null) return Angles.wrapAll(y);
            double[] seed = recover(free, p.diskGx, p.diskGz, spec, sc);
            return SlpSolve.optimizeBestEffort(exact, spec, feasTol, cancel, seed, SLP_PHASE1, SLP_TOTAL, true);
        }
        if (p.keepAlive != null) {
            double[] seed = recover(free, p.diskGx, p.diskGz, spec, sc);
            List<JumpLinearModel.Wall> walls = free.compileWalls(spec.constraints, 0.0, new boolean[]{false});
            walls.add(p.keepAlive);
            return Angles.wrapAll(gnRestore(free, walls, seed, cancel));
        }
        double[] y = ClosedFormSolve.optimize(exact, spec, feasTol, cancel);
        if (y != null) return Angles.wrapAll(y);
        if (reference != null) return Angles.wrapAll(reference);
        return null;
    }

    private static double[] treeCompletion(ExactJumpModel exact, JumpSpec spec, double feasTol,
                                           AtomicBoolean cancel, long deadlineNanos, double[] incumbent) {
        long budget = remaining(deadlineNanos);
        if (budget <= TREE_MIN_BUDGET_NANOS) return null;
        double[] tree = BoundPrunedRecovery.solve(exact, spec, feasTol, cancel, budget,
                Double.NaN, new BoundPrunedRecovery.Config());
        return tree != null ? Angles.wrapAll(tree) : null;
    }

    private static boolean crossCheckInfeasible(JumpSpec spec, JumpLinearModel free,
                                                List<JumpLinearModel.Wall> freeWalls) {
        int n = free.n;
        double[] cx = new double[n];
        double[] cz = new double[n];
        free.objectiveVectors(spec.objective, cx, cz);
        CostateDualSolver.Result r = new CostateDualSolver(n, cx, cz, free.mMagAll(), freeWalls).solve(0.0, null);
        return r == null;
    }

    private static double[] recover(JumpLinearModel lin, double[] gx, double[] gz, JumpSpec spec,
                                    JumpPhysicsInputs sc) {
        int n = lin.n;
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        double[] yaws = new double[n];
        for (int t = 0; t < n; t++) {
            double x = gx != null ? gx[t] : 0.0;
            double z = gz != null ? gz[t] : 0.0;
            if (x * x + z * z < 1.0e-18) {
                if (spec.objective.axis == JumpPhysicsInputs.Axis.X) { x = max ? 1.0 : -1.0; z = 0.0; }
                else { x = 0.0; z = max ? 1.0 : -1.0; }
            }
            yaws[t] = lin.recoverYawDeg(t, x, z);
        }
        return Angles.wrapAll(yaws);
    }

    private static double[] gnRestore(JumpLinearModel lin, List<JumpLinearModel.Wall> walls, double[] seed,
                                      AtomicBoolean cancel) {
        int n = lin.n;
        int m = walls.size();
        double[] theta = Angles.wrapAll(seed);
        double damp = 1.0e-3;
        double best = Double.MAX_VALUE;
        double[] bestTheta = theta.clone();
        double[] ux = new double[n];
        double[] uz = new double[n];
        double[][] a = new double[n][n];
        double[] b = new double[n];
        for (int it = 0; it < RESTORE_ITERS; it++) {
            if (cancel != null && cancel.get()) break;
            for (int t = 0; t < n; t++) {
                double phi = lin.baseArg(t) + theta[t] * RAD;
                ux[t] = lin.mMag(t) * Math.cos(phi);
                uz[t] = lin.mMag(t) * Math.sin(phi);
            }
            double cur = wallViol(walls, ux, uz);
            if (cur < best) { best = cur; bestTheta = theta.clone(); }
            if (cur <= 0.0) break;
            for (int p = 0; p < n; p++) { java.util.Arrays.fill(a[p], 0.0); b[p] = 0.0; }
            for (int j = 0; j < m; j++) {
                JumpLinearModel.Wall w = walls.get(j);
                double au = 0.0;
                for (int t = 0; t < n; t++) au += w.coef[t] * (w.axis == 0 ? ux[t] : uz[t]);
                double resid = Math.max(0.0, au - w.bPrime + RESTORE_AIM);
                if (resid == 0.0) continue;
                for (int t = 0; t < n; t++) {
                    double jt = w.coef[t] * (w.axis == 0 ? -uz[t] : ux[t]) * RAD;
                    if (jt == 0.0) continue;
                    b[t] -= jt * resid;
                    for (int t2 = t; t2 < n; t2++) {
                        double jt2 = w.coef[t2] * (w.axis == 0 ? -uz[t2] : ux[t2]) * RAD;
                        a[t][t2] += jt * jt2;
                    }
                }
            }
            double maxDiag = 0.0;
            for (int t = 0; t < n; t++) {
                for (int t2 = 0; t2 < t; t2++) a[t][t2] = a[t2][t];
                if (a[t][t] > maxDiag) maxDiag = a[t][t];
            }
            if (maxDiag <= 0.0) break;
            double[] d = choleskySolve(a, b, n, damp * maxDiag);
            if (d == null) { damp = Math.min(damp * 10.0, 1.0e6); continue; }
            double step = 0.0;
            for (double dv : d) step = Math.max(step, Math.abs(dv));
            if (step > RESTORE_STEP_CAP_DEG) {
                double scale = RESTORE_STEP_CAP_DEG / step;
                for (int t = 0; t < n; t++) d[t] *= scale;
            }
            double[] cand = new double[n];
            for (int t = 0; t < n; t++) cand[t] = Angles.wrap(theta[t] + d[t]);
            double[] cux = new double[n];
            double[] cuz = new double[n];
            for (int t = 0; t < n; t++) {
                double phi = lin.baseArg(t) + cand[t] * RAD;
                cux[t] = lin.mMag(t) * Math.cos(phi);
                cuz[t] = lin.mMag(t) * Math.sin(phi);
            }
            if (wallViol(walls, cux, cuz) < cur) { theta = cand; damp = Math.max(damp * 0.5, 1.0e-8); }
            else damp = Math.min(damp * 10.0, 1.0e7);
        }
        return bestTheta;
    }

    private static double wallViol(List<JumpLinearModel.Wall> walls, double[] ux, double[] uz) {
        double mx = 0.0;
        for (JumpLinearModel.Wall w : walls) {
            double au = 0.0;
            for (int t = 0; t < ux.length; t++) au += w.coef[t] * (w.axis == 0 ? ux[t] : uz[t]);
            double s = au - w.bPrime;
            if (s > mx) mx = s;
        }
        return mx;
    }

    private static double[] choleskySolve(double[][] a, double[] b, int n, double damp) {
        double[][] l = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double s = a[i][j] + (i == j ? damp : 0.0);
                for (int k = 0; k < j; k++) s -= l[i][k] * l[j][k];
                if (i == j) {
                    if (s <= 0.0) return null;
                    l[i][i] = Math.sqrt(s);
                } else {
                    l[i][j] = s / l[j][j];
                }
            }
        }
        double[] x = b.clone();
        for (int i = 0; i < n; i++) {
            double s = x[i];
            for (int k = 0; k < i; k++) s -= l[i][k] * x[k];
            x[i] = s / l[i][i];
        }
        for (int i = n - 1; i >= 0; i--) {
            double s = x[i];
            for (int k = i + 1; k < n; k++) s -= l[k][i] * x[k];
            x[i] = s / l[i][i];
        }
        return x;
    }

    private static double[] referenceTrajectory(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                                JumpLinearModel free, double[] seedBaseline, double feasTol,
                                                AtomicBoolean cancel) {
        if (seedBaseline != null) return Angles.wrapAll(seedBaseline);
        double[] y = ClosedFormSolve.optimize(exact, spec, feasTol, cancel);
        if (y != null) return Angles.wrapAll(y);
        int n = free.n;
        double[] cx = new double[n];
        double[] cz = new double[n];
        free.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> walls = free.compileWalls(spec.constraints, 0.0, trivial);
        if (trivial[0]) return null;
        CostateDualSolver.Result r = new CostateDualSolver(n, cx, cz, free.mMagAll(), walls).solve(0.0, null);
        if (r == null) return null;
        return recover(free, r.gx, r.gz, spec, sc);
    }

    private static double normedObjective(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec,
                                          double[] yaws, boolean max) {
        double v = objectiveOf(exact, sc, spec, yaws);
        return max ? v : -v;
    }

    private static double objectiveOf(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec, double[] yaws) {
        double[] gf = sc.toGameFacings(yaws);
        return exact.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
    }

    private static double violationOf(ExactJumpModel exact, JumpPhysicsInputs sc,
                                      JumpConstraintCompiler.Compiled compiled, double[] yaws) {
        double[] gf = sc.toGameFacings(yaws);
        return compiled.maxViolation(gf, exact.forward(sc, gf));
    }

    private static boolean better(double a, double b, boolean max) {
        return max ? a > b : a < b;
    }

    private static boolean expired(AtomicBoolean cancel, long deadlineNanos) {
        return (cancel != null && cancel.get()) || (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos);
    }

    private static long remaining(long deadlineNanos) {
        return deadlineNanos == 0L ? 0L : deadlineNanos - System.nanoTime();
    }
}
