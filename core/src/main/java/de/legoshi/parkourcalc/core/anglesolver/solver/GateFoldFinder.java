package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GateFoldFinder {

    public static boolean DEBUG = false;

    private static final double RAD = Math.PI / 180.0;
    private static final double START_SCALE = 0.03;
    private static final double BAND_LO = 0.2;
    private static final double BAND_HI = 5.0;
    private static final int MAX_BITS = 6;
    private static final int SLP_ITERS = 110;
    private static final int STALL_LIMIT = 24;
    private static final double GATE_MERIT_W = 6.0;
    private static final double POLISH_TRIGGER = 0.05;
    private static final int DEEPEN_ROUNDS = 4;
    private static final int OBJ_DEEPEN_ROUNDS = 2;
    private static final double TR_START = 20.0;
    private static final double TR_MAX = 45.0;
    private static final double TR_MIN = 1.0e-8;
    private static final int OBJ_ITERS = 60;
    private static final double OBJ_TR_START = 8.0;
    private static final int HOMO_RUNGS = 12;
    private static final int HOMO_ITERS = 34;
    private static final double HOMO_START_STEP = 45.0;
    private static final long HOMO_BUDGET_NANOS = 6_000_000_000L;
    private static final double HOMO_DEDUP_DEG = 0.5;
    private static final int HOMO_SEED_CAP = 4;

    public static final class Result {
        public final double[] yawsDeg;
        public final double px;
        public final double pz;
        public final double objective;
        public final double viol;

        Result(double[] yawsDeg, double px, double pz, double objective, double viol) {
            this.yawsDeg = yawsDeg;
            this.px = px;
            this.pz = pz;
            this.objective = objective;
            this.viol = viol;
        }

        public boolean feasible() {
            return viol == 0.0;
        }
    }

    private GateFoldFinder() {
    }

    public static Result solve(ExactJumpModel exact, JumpSpec spec, YawTies ties,
                               AtomicBoolean cancel, long deadlineNanos) {
        return solve(exact, spec, ties, cancel, deadlineNanos, false, false);
    }

    public static Result solve(ExactJumpModel exact, JumpSpec spec, YawTies ties,
                               AtomicBoolean cancel, long deadlineNanos, boolean stopOnFeasible) {
        return solve(exact, spec, ties, cancel, deadlineNanos, stopOnFeasible, false);
    }

    public static Result solve(ExactJumpModel exact, JumpSpec spec, YawTies ties,
                               AtomicBoolean cancel, long deadlineNanos, boolean stopOnFeasible, boolean homotopy) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        if (ties == null || !JumpLinearModel.hasFacingWall(spec.constraints)) return null;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        boolean optimize = !stopOnFeasible;
        StartBox box = sc.startBox;
        boolean free = box != null && box.startFree();
        double thr = exact.inertiaThreshold();
        boolean perAxis = exact.perAxisInertia();

        double[] baseline = SlpSolve.optimizeBestEffort(exact, spec, 0.0, cancel, null, 120, 200, true);
        if (baseline == null) baseline = seedFromDual(exact, spec, sc, ties);
        if (baseline == null) return null;

        Result best = evalAbsolute(exact, sc, compiled, spec, baseline, box, free);
        best = betterFeas(best, translatePolish(exact, sc, compiled, spec, baseline, box, free, optimize, max), max);

        List<double[]> seeds = buildSeeds(exact, spec, sc, ties, baseline, cancel, deadlineNanos);
        if (homotopy && deadlineNanos != 0L) {
            long homoDeadline = Math.min(deadlineNanos, System.nanoTime() + HOMO_BUDGET_NANOS);
            List<double[]> homo = homotopySeeds(exact, spec, sc, ties, baseline, box, free, perAxis, thr,
                    cancel, homoDeadline);
            for (double[] hy : homo) {
                best = betterFeas(best, evalAbsolute(exact, sc, compiled, spec, hy, box, free), max);
                best = betterFeas(best, translatePolish(exact, sc, compiled, spec, hy, box, free, optimize, max), max);
            }
            if (stopOnFeasible && !homo.isEmpty()) {
                homo.sort((a, b) -> Double.compare(
                        evalAbsolute(exact, sc, compiled, spec, a, box, free).viol,
                        evalAbsolute(exact, sc, compiled, spec, b, box, free).viol));
                int cap = Math.min(homo.size(), HOMO_SEED_CAP);
                seeds.addAll(0, new ArrayList<double[]>(homo.subList(0, cap)));
            }
        }
        for (double[] seed : seeds) {
            if (cancel != null && cancel.get()) break;
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
            Result before = best;
            best = betterFeas(best, enumerateFromSeed(exact, spec, sc, compiled, ties, seed, box, free, max,
                    perAxis, thr, cancel, deadlineNanos, stopOnFeasible, best), max);
            if (best != before && best != null && (best.viol > 0.0 && best.viol < POLISH_TRIGGER)) {
                best = betterFeas(best, latticePolish(exact, spec, sc, compiled, ties, best, box, free, max,
                        optimize, cancel, deadlineNanos), max);
            }
            if (stopOnFeasible && best != null && best.feasible()) break;
        }
        for (int round = 0; round < DEEPEN_ROUNDS && best != null && !best.feasible(); round++) {
            if (cancel != null && cancel.get()) break;
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
            double prev = best.viol;
            best = enumerateFromSeed(exact, spec, sc, compiled, ties, best.yawsDeg, box, free, max,
                    perAxis, thr, cancel, deadlineNanos, stopOnFeasible, best);
            if (best != null && best.viol > 0.0 && best.viol < POLISH_TRIGGER) {
                best = betterFeas(best, latticePolish(exact, spec, sc, compiled, ties, best, box, free, max,
                        optimize, cancel, deadlineNanos), max);
            }
            if (best == null || best.feasible() || best.viol >= prev - 1.0e-6) break;
        }
        if (optimize && best != null && best.feasible()) {
            best = betterFeas(best, latticePolish(exact, spec, sc, compiled, ties, best, box, free, max,
                    true, cancel, deadlineNanos), max);
            for (int round = 0; round < OBJ_DEEPEN_ROUNDS; round++) {
                if (cancel != null && cancel.get()) break;
                if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
                double prevObj = best.objective;
                Result deep = enumerateFromSeed(exact, spec, sc, compiled, ties, best.yawsDeg, box, free, max,
                        perAxis, thr, cancel, deadlineNanos, false, best);
                best = betterFeas(best, deep, max);
                if (best.feasible()) {
                    best = betterFeas(best, latticePolish(exact, spec, sc, compiled, ties, best, box, free, max,
                            true, cancel, deadlineNanos), max);
                }
                if (!(betterObjLat(best.objective, prevObj, max) && Math.abs(best.objective - prevObj) > 1.0e-9)) break;
            }
        }
        return best;
    }

    private static Result latticePolish(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                        JumpConstraintCompiler.Compiled compiled, YawTies ties, Result start,
                                        StartBox box, boolean free, boolean max, boolean optimize,
                                        AtomicBoolean cancel, long deadlineNanos) {
        int n = sc.numTicks;
        int dims = ties.dims();
        double[] red = ties.reduce(start.yawsDeg);
        double px = start.px;
        double pz = start.pz;
        double bestViol = start.viol;
        double bestObj = start.objective;
        double step = 1.0 / SineTableGeometry.IDX_PER_DEG;
        int half = 96;
        double pxStep = free ? (box.pxHi - box.pxLo) / 256.0 : 0.0;
        double pzStep = free ? (box.pzHi - box.pzLo) / 256.0 : 0.0;
        boolean objSweep = optimize && bestViol == 0.0;
        boolean improved = true;
        for (int pass = 0; pass < 8 && improved; pass++) {
            improved = false;
            if (cancel != null && cancel.get()) break;
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
            for (int v = 0; v < dims; v++) {
                double center = red[v];
                double bestCand = center;
                for (int i = -half; i <= half; i++) {
                    double cand = center + i * step;
                    red[v] = cand;
                    double[] full = ties.expand(red);
                    double vio = specViol(exact, sc, compiled, full, px, pz);
                    if (vio < bestViol - 1.0e-12
                            || (vio == bestViol && betterObjLat(objOf(exact, sc, spec, full, px, pz), bestObj, max))) {
                        bestViol = vio;
                        bestObj = objOf(exact, sc, spec, full, px, pz);
                        bestCand = cand;
                        improved = true;
                    }
                }
                red[v] = bestCand;
            }
            if (free) {
                double[] full = ties.expand(red);
                for (int axis = 0; axis < 2; axis++) {
                    double cur = axis == 0 ? px : pz;
                    double st = axis == 0 ? pxStep : pzStep;
                    double lo = axis == 0 ? box.pxLo : box.pzLo;
                    double hi = axis == 0 ? box.pxHi : box.pzHi;
                    double bestCand = cur;
                    for (int i = -half; i <= half; i++) {
                        double cand = clamp(cur + i * st, lo, hi);
                        double npx = axis == 0 ? cand : px;
                        double npz = axis == 0 ? pz : cand;
                        double vio = specViol(exact, sc, compiled, full, npx, npz);
                        boolean take = vio < bestViol - 1.0e-12;
                        if (!take && objSweep && vio == 0.0 && bestViol == 0.0) {
                            take = betterObjLat(objOf(exact, sc, spec, full, npx, npz), bestObj, max);
                        }
                        if (take) {
                            bestViol = vio;
                            if (objSweep && vio == 0.0) bestObj = objOf(exact, sc, spec, full, npx, npz);
                            bestCand = cand;
                            improved = true;
                        }
                    }
                    if (axis == 0) px = bestCand; else pz = bestCand;
                }
            }
        }
        double[] full = ties.expand(red);
        return new Result(Angles.wrapAll(full), px, pz, objOf(exact, sc, spec, full, px, pz),
                specViol(exact, sc, compiled, full, px, pz));
    }

    private static boolean betterObjLat(double a, double b, boolean max) {
        return max ? a > b : a < b;
    }

    private static List<double[]> buildSeeds(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                             YawTies ties, double[] baseline, AtomicBoolean cancel, long deadline) {
        List<double[]> seeds = new ArrayList<double[]>();
        seeds.add(baseline);
        int dims = ties.dims();
        int nbig = Math.min(2, dims);
        for (int v = 0; v < nbig; v++) {
            boolean[] lock = new boolean[dims];
            lock[v] = true;
            for (double ang = 0.0; ang < 360.0; ang += 45.0) {
                if (cancel != null && cancel.get()) return seeds;
                if (deadline != 0L && System.nanoTime() >= deadline) return seeds;
                double[] s = baseline.clone();
                for (int t = 0; t < sc.numTicks; t++) if (ties.varOf(t) == v) s[t] = ang + ties.offsetOf(t);
                double[] r = SlpSolve.optimizeLocked(exact, spec, 0.0, cancel, s, lock, 60, 100, true);
                if (r != null) seeds.add(r);
            }
        }
        return seeds;
    }

    private static List<double[]> homotopySeeds(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                                YawTies ties, double[] baseline, StartBox box, boolean free,
                                                boolean perAxis, double realThr, AtomicBoolean cancel,
                                                long deadlineNanos) {
        List<double[]> out = new ArrayList<double[]>();
        List<double[]> reducedKept = new ArrayList<double[]>();
        int n = sc.numTicks;
        int dims = ties.dims();
        double refPx = box != null ? box.px : sc.startPos.x;
        double refPz = box != null ? box.pz : sc.startPos.z;
        JumpLinearModel linFull = new JumpLinearModel(sc, null, null);

        int vBig = 0;
        int bigCount = -1;
        for (int v = 0; v < dims; v++) {
            int c = 0;
            for (int t = 0; t < n; t++) if (ties.varOf(t) == v) c++;
            if (c > bigCount) {
                bigCount = c;
                vBig = v;
            }
        }

        List<double[]> starts = new ArrayList<double[]>();
        starts.add(ties.expand(ties.reduce(baseline.clone())));
        for (double ang = 0.0; ang < 360.0; ang += HOMO_START_STEP) {
            double[] red = ties.reduce(baseline.clone());
            red[vBig] = ang;
            starts.add(ties.expand(red));
        }

        for (double[] start : starts) {
            if (cancel != null && cancel.get()) break;
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
            double[] theta = start.clone();
            double px = refPx;
            double pz = refPz;
            for (int r = 0; r <= HOMO_RUNGS; r++) {
                if (cancel != null && cancel.get()) break;
                if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
                double thrRung = realThr * Math.pow(2.0, -(HOMO_RUNGS - r));
                boolean[] zx = new boolean[n];
                boolean[] zz = new boolean[n];
                linFull.zeroingPattern(Angles.wrapAll(theta), thrRung, perAxis, zx, zz);
                double[] step = continuationStep(sc, ties, zx, zz, spec, theta, px, pz, box, free,
                        refPx, refPz, thrRung, cancel, deadlineNanos);
                if (step == null) continue;
                System.arraycopy(step, 0, theta, 0, n);
                px = step[n];
                pz = step[n + 1];
            }
            double[] red = ties.reduce(Angles.wrapAll(theta));
            if (isNovel(reducedKept, red)) {
                reducedKept.add(red);
                out.add(Angles.wrapAll(theta));
            }
        }
        return out;
    }

    private static boolean isNovel(List<double[]> kept, double[] red) {
        for (double[] k : kept) {
            double worst = 0.0;
            for (int i = 0; i < red.length; i++) {
                double dd = Math.abs(Angles.wrap(red[i] - k[i]));
                if (dd > worst) worst = dd;
            }
            if (worst <= HOMO_DEDUP_DEG) return false;
        }
        return true;
    }

    private static double[] continuationStep(JumpPhysicsInputs sc, YawTies ties, boolean[] zx, boolean[] zz,
                                             JumpSpec spec, double[] thetaFull, double px0, double pz0,
                                             StartBox box, boolean free, double refPx, double refPz,
                                             double thrRung, AtomicBoolean cancel, long deadlineNanos) {
        int n = sc.numTicks;
        JumpLinearModel lin = new JumpLinearModel(sc, zx, zz);
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> walls =
                new ArrayList<JumpLinearModel.Wall>(lin.compileWalls(spec.constraints, 0.0, trivial));
        if (trivial[0]) return null;
        walls.addAll(lin.velocityWalls(thrRung));
        int m = walls.size();
        if (m == 0) return null;
        int dims = ties.dims();
        int vars = free ? dims + 2 : dims;
        int[] col = new int[n];
        for (int t = 0; t < n; t++) col[t] = ties.varOf(t);
        double[] theta = thetaFull.clone();
        double px = px0;
        double pz = pz0;
        double[] ux = new double[n];
        double[] uz = new double[n];
        double tr = TR_START;
        int stall = 0;
        double curViol = linResidual(lin, walls, theta, px, pz, refPx, refPz, free, ux, uz);
        for (int it = 0; it < HOMO_ITERS; it++) {
            if (cancel != null && cancel.get()) break;
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
            if (curViol <= 0.0) break;
            for (int t = 0; t < n; t++) {
                double phi = lin.baseArg(t) + theta[t] * RAD;
                ux[t] = lin.mMag(t) * Math.cos(phi);
                uz[t] = lin.mMag(t) * Math.sin(phi);
            }
            double[][] rows = new double[m][vars];
            double[] wv = new double[m];
            for (int j = 0; j < m; j++) {
                JumpLinearModel.Wall w = walls.get(j);
                double val = -w.bPrime;
                double[] cf = w.coef;
                for (int t = 0; t < n; t++) {
                    double c = t < cf.length ? cf[t] : 0.0;
                    if (c == 0.0) continue;
                    double uAxis = w.axis == 0 ? ux[t] : uz[t];
                    double dAxis = w.axis == 0 ? -uz[t] : ux[t];
                    val += c * uAxis;
                    int v = col[t];
                    if (v >= 0) rows[j][v] += c * dAxis * RAD;
                }
                if (free && w.p0coef != 0.0) {
                    int sc0 = w.axis == 0 ? dims : dims + 1;
                    double dstart = w.axis == 0 ? (px - refPx) : (pz - refPz);
                    val += -w.p0coef * dstart;
                    rows[j][sc0] += -w.p0coef * START_SCALE;
                }
                wv[j] = val;
            }
            TrustRegionLp.Result lp = TrustRegionLp.solve(rows, wv, null, tr, true, -1.0e-9, 2000);
            if (lp == null) break;
            double[] d = lp.d;
            double[] cand = theta.clone();
            for (int t = 0; t < n; t++) {
                int v = col[t];
                if (v >= 0) cand[t] = theta[t] + d[v];
            }
            double cpx = px;
            double cpz = pz;
            if (free) {
                cpx = clamp(px + d[dims] * START_SCALE, box.pxLo, box.pxHi);
                cpz = clamp(pz + d[dims + 1] * START_SCALE, box.pzLo, box.pzHi);
            }
            double candViol = linResidual(lin, walls, cand, cpx, cpz, refPx, refPz, free, ux, uz);
            double step = 0.0;
            for (int v = 0; v < vars; v++) step = Math.max(step, Math.abs(d[v]));
            if (candViol < curViol - 1.0e-12) {
                theta = cand;
                px = cpx;
                pz = cpz;
                curViol = candViol;
                stall = 0;
                if (step > 0.8 * tr) tr = Math.min(tr * 2.0, TR_MAX);
            } else {
                tr *= 0.5;
                if (tr < TR_MIN || ++stall >= STALL_LIMIT) break;
            }
        }
        double[] outp = new double[n + 2];
        System.arraycopy(theta, 0, outp, 0, n);
        outp[n] = px;
        outp[n + 1] = pz;
        return outp;
    }

    private static double linResidual(JumpLinearModel lin, List<JumpLinearModel.Wall> walls, double[] theta,
                                      double px, double pz, double refPx, double refPz, boolean free,
                                      double[] ux, double[] uz) {
        int n = theta.length;
        for (int t = 0; t < n; t++) {
            double phi = lin.baseArg(t) + theta[t] * RAD;
            ux[t] = lin.mMag(t) * Math.cos(phi);
            uz[t] = lin.mMag(t) * Math.sin(phi);
        }
        double worst = 0.0;
        for (JumpLinearModel.Wall w : walls) {
            double val = -w.bPrime;
            double[] cf = w.coef;
            for (int t = 0; t < n; t++) {
                double c = t < cf.length ? cf[t] : 0.0;
                if (c == 0.0) continue;
                val += c * (w.axis == 0 ? ux[t] : uz[t]);
            }
            if (free && w.p0coef != 0.0) {
                double dstart = w.axis == 0 ? (px - refPx) : (pz - refPz);
                val += -w.p0coef * dstart;
            }
            double r = w.eq ? Math.abs(val) : val;
            if (r > worst) worst = r;
        }
        return worst;
    }

    private static Result enumerateFromSeed(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                            JumpConstraintCompiler.Compiled compiled, YawTies ties, double[] seed,
                                            StartBox box, boolean free, boolean max, boolean perAxis, double thr,
                                            AtomicBoolean cancel, long deadlineNanos, boolean stopOnFeasible,
                                            Result best) {
        int n = sc.numTicks;
        boolean optimize = !stopOnFeasible;
        ForwardPath basePath = exact.forward(sc, sc.toGameFacings(seed));
        boolean[] baseZeroX = new boolean[n];
        boolean[] baseZeroZ = new boolean[n];
        FoldReplayDriver.extractPattern(exact, basePath, n, baseZeroX, baseZeroZ);
        best = betterFeas(best, translatePolish(exact, sc, compiled, spec, seed, box, free, optimize, max), max);

        List<int[]> bits = new ArrayList<int[]>();
        for (int t = 1; t < n && bits.size() < MAX_BITS; t++) {
            addBit(bits, basePath, t, 0, thr, baseZeroX[t]);
            if (bits.size() < MAX_BITS) addBit(bits, basePath, t, 1, thr, baseZeroZ[t]);
        }
        int k = bits.size();
        for (int combo = 0; combo < (1 << k); combo++) {
            if (cancel != null && cancel.get()) break;
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
            boolean[] zx = baseZeroX.clone();
            boolean[] zz = baseZeroZ.clone();
            boolean changed = false;
            for (int i = 0; i < k; i++) {
                if ((combo & (1 << i)) == 0) continue;
                int[] b = bits.get(i);
                if (b[1] == 0) zx[b[0]] = !zx[b[0]]; else zz[b[0]] = !zz[b[0]];
                changed = true;
            }
            if (!changed && combo != 0) continue;
            if (!perAxis) for (int t = 0; t < n; t++) { boolean z = zx[t] || zz[t]; zx[t] = z; zz[t] = z; }
            Result r = solvePattern(exact, spec, sc, compiled, ties, zx, zz, seed, box, free, max,
                    optimize, cancel, deadlineNanos);
            if (DEBUG && r != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < k; i++) if ((combo & (1 << i)) != 0) {
                    int[] b = bits.get(i);
                    sb.append(b[1] == 0 ? 'X' : 'Z').append(b[0]).append(' ');
                }
                System.out.printf("  combo forced[%s] viol=%.6e obj=%.5f%n", sb.toString().trim(), r.viol, r.objective);
            }
            best = betterFeas(best, r, max);
            if (stopOnFeasible && best != null && best.feasible()) break;
        }
        return best;
    }

    private static void addBit(List<int[]> bits, ForwardPath path, int t, int axis, double thr, boolean zero) {
        double v = Math.abs(axis == 0 ? path.velX[t] : path.velZ[t]);
        boolean flip = false;
        double pv = axis == 0 ? path.velX[t - 1] : path.velZ[t - 1];
        double cv = axis == 0 ? path.velX[t] : path.velZ[t];
        if (pv * cv < 0.0) flip = true;
        if (!zero && (flip || (v >= BAND_LO * thr && v <= BAND_HI * thr))) bits.add(new int[]{t, axis});
    }

    private static Result solvePattern(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                       JumpConstraintCompiler.Compiled compiled, YawTies ties,
                                       boolean[] zx, boolean[] zz, double[] seed, StartBox box, boolean free,
                                       boolean max, boolean optimize, AtomicBoolean cancel, long deadlineNanos) {
        int n = sc.numTicks;
        JumpLinearModel lin = new JumpLinearModel(sc, zx, zz);
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> specWalls = lin.compileWalls(spec.constraints, 0.0, trivial);
        if (trivial[0]) return null;
        List<JumpLinearModel.Wall> gateWalls = lin.velocityWalls(exact.inertiaThreshold());
        List<JumpLinearModel.Wall> walls = new ArrayList<JumpLinearModel.Wall>(specWalls);
        walls.addAll(gateWalls);
        int m = walls.size();
        if (m == 0) return null;

        int dims = ties.dims();
        int vars = free ? dims + 2 : dims;
        int[] col = new int[n];
        for (int t = 0; t < n; t++) col[t] = ties.varOf(t);

        double refPx = box != null ? box.px : sc.startPos.x;
        double refPz = box != null ? box.pz : sc.startPos.z;
        double[] theta = ties.expand(ties.reduce(seed.clone()));
        double px = refPx;
        double pz = refPz;

        int[] forcedTicks = forcedList(zx, zz, exact.perAxisInertia());
        double[] ux = new double[n];
        double[] uz = new double[n];
        double tr = TR_START;
        Result best = null;
        double bestSpec = Double.POSITIVE_INFINITY;
        double curMerit = merit(exact, sc, compiled, spec, theta, px, pz, forcedTicks, exact.inertiaThreshold());
        int stall = 0;

        for (int it = 0; it < SLP_ITERS; it++) {
            if (cancel != null && cancel.get()) break;
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
            for (int t = 0; t < n; t++) {
                double phi = lin.baseArg(t) + theta[t] * RAD;
                ux[t] = lin.mMag(t) * Math.cos(phi);
                uz[t] = lin.mMag(t) * Math.sin(phi);
            }
            double[][] rows = new double[m][vars];
            double[] wv = new double[m];
            for (int j = 0; j < m; j++) {
                JumpLinearModel.Wall w = walls.get(j);
                double val = -w.bPrime;
                double[] cf = w.coef;
                for (int t = 0; t < n; t++) {
                    double c = t < cf.length ? cf[t] : 0.0;
                    if (c == 0.0) continue;
                    double uAxis = w.axis == 0 ? ux[t] : uz[t];
                    double dAxis = w.axis == 0 ? -uz[t] : ux[t];
                    val += c * uAxis;
                    int v = col[t];
                    if (v >= 0) rows[j][v] += c * dAxis * RAD;
                }
                if (free && w.p0coef != 0.0) {
                    int sc0 = w.axis == 0 ? dims : dims + 1;
                    double dstart = w.axis == 0 ? (px - refPx) : (pz - refPz);
                    val += -w.p0coef * dstart;
                    rows[j][sc0] += -w.p0coef * START_SCALE;
                }
                wv[j] = val;
            }
            double curSpec = specViol(exact, sc, compiled, theta, px, pz);
            if (curSpec < bestSpec) {
                bestSpec = curSpec;
                best = new Result(Angles.wrapAll(theta), px, pz, objOf(exact, sc, spec, theta, px, pz), curSpec);
            }
            if (curSpec == 0.0) break;

            TrustRegionLp.Result lp = TrustRegionLp.solve(rows, wv, null, tr, true, -1.0e-9, 2000);
            if (lp == null) break;
            double[] d = lp.d;
            double[] cand = theta.clone();
            for (int t = 0; t < n; t++) {
                int v = col[t];
                if (v >= 0) cand[t] = theta[t] + d[v];
            }
            double cpx = px;
            double cpz = pz;
            if (free) {
                cpx = clamp(px + d[dims] * START_SCALE, box.pxLo, box.pxHi);
                cpz = clamp(pz + d[dims + 1] * START_SCALE, box.pzLo, box.pzHi);
            }
            double candMerit = merit(exact, sc, compiled, spec, cand, cpx, cpz, forcedTicks, exact.inertiaThreshold());
            double step = 0.0;
            for (int v = 0; v < vars; v++) step = Math.max(step, Math.abs(d[v]));
            if (candMerit < curMerit - 1.0e-12) {
                theta = cand;
                px = cpx;
                pz = cpz;
                curMerit = candMerit;
                stall = 0;
                if (step > 0.8 * tr) tr = Math.min(tr * 2.0, TR_MAX);
            } else {
                tr *= 0.5;
                if (tr < TR_MIN || ++stall >= STALL_LIMIT) break;
            }
        }
        if (optimize && best != null && bestSpec == 0.0) {
            Result asc = objectiveAscent(exact, spec, sc, compiled, ties, lin, walls, box, free, max,
                    refPx, refPz, best.yawsDeg, best.px, best.pz, cancel, deadlineNanos);
            best = betterFeas(best, asc, max);
        }
        double[] tpTheta = best != null ? best.yawsDeg : theta;
        Result tp = translatePolish(exact, sc, compiled, spec, tpTheta, box, free, optimize, max);
        best = betterFeas(best, tp, max);
        return best;
    }

    private static Result objectiveAscent(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                          JumpConstraintCompiler.Compiled compiled, YawTies ties, JumpLinearModel lin,
                                          List<JumpLinearModel.Wall> walls, StartBox box,
                                          boolean free, boolean max, double refPx, double refPz, double[] seedYaws,
                                          double px0, double pz0, AtomicBoolean cancel, long deadlineNanos) {
        int n = sc.numTicks;
        int m = walls.size();
        int dims = ties.dims();
        int vars = free ? dims + 2 : dims;
        int[] col = new int[n];
        for (int t = 0; t < n; t++) col[t] = ties.varOf(t);
        double[] objCx = new double[n];
        double[] objCz = new double[n];
        lin.objectiveVectors(spec.objective, objCx, objCz);
        double objStartX = objStartGrad(spec.objective, 0);
        double objStartZ = objStartGrad(spec.objective, 1);

        double[] theta = ties.expand(ties.reduce(seedYaws.clone()));
        double px = px0;
        double pz = pz0;
        double[] ux = new double[n];
        double[] uz = new double[n];
        double tr = OBJ_TR_START;
        int stall = 0;
        double bestObj = objOf(exact, sc, spec, theta, px, pz);
        Result best = new Result(Angles.wrapAll(theta), px, pz, bestObj, specViol(exact, sc, compiled, theta, px, pz));
        if (!best.feasible()) return null;

        for (int it = 0; it < OBJ_ITERS; it++) {
            if (cancel != null && cancel.get()) break;
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
            for (int t = 0; t < n; t++) {
                double phi = lin.baseArg(t) + theta[t] * RAD;
                ux[t] = lin.mMag(t) * Math.cos(phi);
                uz[t] = lin.mMag(t) * Math.sin(phi);
            }
            double[][] rows = new double[m][vars];
            double[] wv = new double[m];
            for (int j = 0; j < m; j++) {
                JumpLinearModel.Wall w = walls.get(j);
                double val = -w.bPrime;
                double[] cf = w.coef;
                for (int t = 0; t < n; t++) {
                    double c = t < cf.length ? cf[t] : 0.0;
                    if (c == 0.0) continue;
                    double uAxis = w.axis == 0 ? ux[t] : uz[t];
                    double dAxis = w.axis == 0 ? -uz[t] : ux[t];
                    val += c * uAxis;
                    int v = col[t];
                    if (v >= 0) rows[j][v] += c * dAxis * RAD;
                }
                if (free && w.p0coef != 0.0) {
                    int sc0 = w.axis == 0 ? dims : dims + 1;
                    double dstart = w.axis == 0 ? (px - refPx) : (pz - refPz);
                    val += -w.p0coef * dstart;
                    rows[j][sc0] += -w.p0coef * START_SCALE;
                }
                wv[j] = val;
            }
            double[] objRow = new double[vars];
            for (int t = 0; t < n; t++) {
                int v = col[t];
                if (v < 0) continue;
                objRow[v] += -(objCx[t] * -uz[t] + objCz[t] * ux[t]) * RAD;
            }
            if (free) {
                objRow[dims] += -objStartX * START_SCALE;
                objRow[dims + 1] += -objStartZ * START_SCALE;
            }
            TrustRegionLp.Result lp = TrustRegionLp.solve(rows, wv, objRow, tr, false, 0.0, 2000);
            if (lp == null) break;
            double[] d = lp.d;
            double[] cand = theta.clone();
            for (int t = 0; t < n; t++) {
                int v = col[t];
                if (v >= 0) cand[t] = theta[t] + d[v];
            }
            double cpx = px;
            double cpz = pz;
            if (free) {
                cpx = clamp(px + d[dims] * START_SCALE, box.pxLo, box.pxHi);
                cpz = clamp(pz + d[dims + 1] * START_SCALE, box.pzLo, box.pzHi);
            }
            double cViol = specViol(exact, sc, compiled, cand, cpx, cpz);
            double cObj = objOf(exact, sc, spec, cand, cpx, cpz);
            double step = 0.0;
            for (int v = 0; v < vars; v++) step = Math.max(step, Math.abs(d[v]));
            if (cViol == 0.0 && betterObjLat(cObj, bestObj, max)) {
                theta = cand;
                px = cpx;
                pz = cpz;
                bestObj = cObj;
                best = new Result(Angles.wrapAll(theta), px, pz, cObj, 0.0);
                stall = 0;
                if (step > 0.8 * tr) tr = Math.min(tr * 2.0, TR_MAX);
            } else {
                tr *= 0.5;
                if (tr < TR_MIN || ++stall >= STALL_LIMIT) break;
            }
        }
        return best;
    }

    private static double objStartGrad(Objective obj, int axis) {
        if (obj.isMotion()) return 0.0;
        double s = obj.sense == Objective.Sense.MAX ? 1.0 : -1.0;
        if (obj.isCustomAngle()) {
            double rad = Math.toRadians(obj.customYaw);
            return axis == 0 ? s * -Math.sin(rad) : s * Math.cos(rad);
        }
        if (obj.axis == JumpPhysicsInputs.Axis.X) return axis == 0 ? s : 0.0;
        return axis == 1 ? s : 0.0;
    }

    private static int[] forcedList(boolean[] zx, boolean[] zz, boolean perAxis) {
        List<Integer> f = new ArrayList<Integer>();
        for (int t = 1; t < zx.length; t++) {
            if (zx[t]) f.add(t * 2);
            if (perAxis && zz[t]) f.add(t * 2 + 1);
        }
        int[] out = new int[f.size()];
        for (int i = 0; i < out.length; i++) out[i] = f.get(i);
        return out;
    }

    private static double merit(ExactJumpModel exact, JumpPhysicsInputs sc,
                                JumpConstraintCompiler.Compiled compiled, JumpSpec spec, double[] theta,
                                double px, double pz, int[] forced, double thr) {
        JumpPhysicsInputs rep = pinned(sc, px, pz);
        double[] gf = rep.toGameFacings(Angles.wrapAll(theta));
        ForwardPath path = exact.forward(rep, gf);
        double sv = compiled.maxViolation(gf, path);
        double gate = 0.0;
        for (int f : forced) {
            int t = f >> 1;
            double v = (f & 1) == 0 ? path.velX[t] : path.velZ[t];
            double over = Math.abs(v) - thr;
            if (over > 0.0) gate += over;
        }
        return sv + GATE_MERIT_W * gate;
    }

    private static double specViol(ExactJumpModel exact, JumpPhysicsInputs sc,
                                   JumpConstraintCompiler.Compiled compiled, double[] theta, double px, double pz) {
        JumpPhysicsInputs rep = pinned(sc, px, pz);
        double[] gf = rep.toGameFacings(Angles.wrapAll(theta));
        return compiled.maxViolation(gf, exact.forward(rep, gf));
    }

    private static double objOf(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec,
                                double[] theta, double px, double pz) {
        JumpPhysicsInputs rep = pinned(sc, px, pz);
        double[] gf = rep.toGameFacings(Angles.wrapAll(theta));
        return exact.forward(rep, gf).getPos(spec.objective.tick, spec.objective.axis);
    }

    private static Result evalAbsolute(ExactJumpModel exact, JumpPhysicsInputs sc,
                                       JumpConstraintCompiler.Compiled compiled, JumpSpec spec, double[] yaws,
                                       StartBox box, boolean free) {
        double px = free ? box.px : (box != null ? box.px : sc.startPos.x);
        double pz = free ? box.pz : (box != null ? box.pz : sc.startPos.z);
        double v = specViol(exact, sc, compiled, yaws, px, pz);
        return new Result(Angles.wrapAll(yaws), px, pz, objOf(exact, sc, spec, yaws, px, pz), v);
    }

    private static Result translatePolish(ExactJumpModel exact, JumpPhysicsInputs sc,
                                          JumpConstraintCompiler.Compiled compiled, JumpSpec spec, double[] theta,
                                          StartBox box, boolean free, boolean optimize, boolean max) {
        if (!free) return null;
        double refPx = box.px;
        double refPz = box.pz;
        double[] yaws = Angles.wrapAll(theta);
        double[] gf0 = pinned(sc, refPx, refPz).toGameFacings(yaws);
        ForwardPath path0 = exact.forward(pinned(sc, refPx, refPz), gf0);
        double loX = box.pxLo - refPx, hiX = box.pxHi - refPx;
        double loZ = box.pzLo - refPz, hiZ = box.pzHi - refPz;
        for (JumpConstraint c : spec.constraints) {
            if (c.mode != JumpConstraint.Mode.X && c.mode != JumpConstraint.Mode.Z) {
                if (JumpConstraintCompiler.slack(c, gf0, path0) > 0.0) return null;
                continue;
            }
            int tc = c.t2 == null ? 1 : (c.op == JumpConstraint.Op.PLUS ? 2 : 0);
            if (tc == 0) {
                if (JumpConstraintCompiler.slack(c, gf0, path0) > 0.0) return null;
                continue;
            }
            double e = JumpConstraintCompiler.evaluate(c, gf0, path0);
            boolean axisX = c.mode == JumpConstraint.Mode.X;
            if (c.cmp == JumpConstraint.Cmp.GE) {
                double lo = -e / tc;
                if (axisX) loX = Math.max(loX, lo); else loZ = Math.max(loZ, lo);
            } else if (c.cmp == JumpConstraint.Cmp.LE) {
                double hi = -e / tc;
                if (axisX) hiX = Math.min(hiX, hi); else hiZ = Math.min(hiZ, hi);
            } else {
                if (e != 0.0) return null;
                if (axisX) { loX = Math.max(loX, 0.0); hiX = Math.min(hiX, 0.0); }
                else { loZ = Math.max(loZ, 0.0); hiZ = Math.min(hiZ, 0.0); }
            }
        }
        if (loX > hiX || loZ > hiZ) return null;
        double npx = refPx + 0.5 * (loX + hiX);
        double npz = refPz + 0.5 * (loZ + hiZ);
        if (optimize && !spec.objective.isCustomAngle() && !spec.objective.isMotion()) {
            if (spec.objective.axis == JumpPhysicsInputs.Axis.X) {
                npx = refPx + (max ? hiX : loX);
            } else {
                npz = refPz + (max ? hiZ : loZ);
            }
        }
        double v = specViol(exact, sc, compiled, yaws, npx, npz);
        return new Result(yaws, npx, npz, objOf(exact, sc, spec, yaws, npx, npz), v);
    }

    private static double[] seedFromDual(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc, YawTies ties) {
        JumpLinearModel lin = new JumpLinearModel(sc);
        double[] cx = new double[lin.n];
        double[] cz = new double[lin.n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivial);
        if (trivial[0]) return null;
        CostateDualSolver.Result r = new CostateDualSolver(lin.n, cx, cz, lin.mMagAll(), walls).solve(0.0, null);
        if (r == null) return null;
        double[] th = lin.recoverAlongCostate(spec.objective, r.gx, r.gz);
        return ties.expand(ties.reduce(th));
    }

    private static Result betterFeas(Result a, Result b, boolean max) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.feasible() != b.feasible()) return a.feasible() ? a : b;
        if (a.feasible()) return (max ? b.objective > a.objective : b.objective < a.objective) ? b : a;
        return b.viol < a.viol ? b : a;
    }

    private static JumpPhysicsInputs pinned(JumpPhysicsInputs sc, double px, double pz) {
        JumpPhysicsInputs c = sc.copy();
        c.startPos = new de.legoshi.parkourcalc.core.sim.Vec3dCore(px, sc.startPos.y, pz);
        c.startBox = StartBox.pinned(px, pz, sc.initialVelocity.x, sc.initialVelocity.z);
        return c;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
