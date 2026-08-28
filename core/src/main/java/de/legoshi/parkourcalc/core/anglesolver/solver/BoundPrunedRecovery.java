package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BoundPrunedRecovery {

    public static boolean DEBUG = false;
    static volatile long debugEpoch;

    private static final double SYNTH_PAD = 1.0e-3;
    private static final double RAD = Math.PI / 180.0;
    private static final double RESTORE_AIM = 1.0e-4;
    private static final double RESTORE_ACTIVE_BAND = 5.0e-3;
    private static final double RESTORE_STEP_CAP_DEG = 20.0;
    private static final double BOUND_QUANTUM = 2.0e-6;
    private static final double TARGET_CONVERT_MAX_CORRIDOR = 2.0e-3;
    private static final int MAX_KEEP_ALIVE = 4;

    public static final class Config {
        public double searchShare = 0.8;
        public double pruneTol = 1.0e-6;
        public double slpViolTrigger = 0.02;
        public double[] seedMargins = {3.0e-4, 1.2e-3, 5.0e-3, 2.0e-2};
        public int maxPatterns = 64;
        public double minSeamWidth = 0.04;
        public int restoreIters = 45;
        public int treeSlpPhase1Calls = 40;
        public int treeSlpTotalCalls = 60;
        public double treeSlpTrMinDeg = 1.0e-3;
        public int polishSlpPhase1Calls = 160;
        public int polishSlpTotalCalls = 220;
    }

    private BoundPrunedRecovery() {
    }

    public static double[] solve(ExactJumpModel exact, JumpSpec spec, double feasTol,
                                 AtomicBoolean cancel, long budgetNanos) {
        return solve(exact, spec, feasTol, cancel, budgetNanos, Double.NaN);
    }

    public static double[] solve(ExactJumpModel exact, JumpSpec spec, double feasTol,
                                 AtomicBoolean cancel, long budgetNanos, double stopAtObjective) {
        return solve(exact, spec, feasTol, cancel, budgetNanos, stopAtObjective, new Config());
    }

    public static double[] solve(ExactJumpModel exact, JumpSpec spec, double feasTol,
                                 AtomicBoolean cancel, long budgetNanos, double stopAtObjective, Config cfg) {
        return solve(exact, spec, feasTol, cancel, budgetNanos, stopAtObjective, cfg, null);
    }

    public static double[] solve(ExactJumpModel exact, JumpSpec spec, double feasTol,
                                 AtomicBoolean cancel, long budgetNanos, double stopAtObjective, Config cfg,
                                 ClosestMiss miss) {
        return solve(exact, spec, feasTol, cancel, budgetNanos, stopAtObjective, cfg, miss, null);
    }

    public static double[] solve(ExactJumpModel exact, JumpSpec spec, double feasTol,
                                 AtomicBoolean cancel, long budgetNanos, double stopAtObjective, Config cfg,
                                 ClosestMiss miss, double[] warmIncumbent) {
        if (spec == null) return null;
        if (JumpLinearModel.hasFacingWall(spec.constraints)
                && FacingPrefold.analyze(spec.constraints, new JumpLinearModel(spec.asScenario())) == null) {
            return null;
        }
        long start = System.nanoTime();
        debugEpoch = start;
        long searchDeadline = start + (long) (budgetNanos * cfg.searchShare);
        long fullDeadline = start + budgetNanos;
        AtomicBoolean outer = cancel != null ? cancel : new AtomicBoolean(false);
        AtomicBoolean searchCancel = new AtomicBoolean(false);
        AtomicBoolean polishCancel = new AtomicBoolean(false);
        AtomicBoolean finished = new AtomicBoolean(false);
        Thread watchdog = startWatchdog(outer, finished, searchCancel, searchDeadline, polishCancel, fullDeadline);
        try {
            JumpPhysicsInputs sc = spec.asScenario();
            boolean max = spec.objective.sense == Objective.Sense.MAX;
            double targetNorm = targetWallNorm(spec, max);
            JumpSpec searchSpec = spec;
            if (!Double.isNaN(targetNorm)) {
                Double freeBound = rootBound(spec, new JumpLinearModel(sc), null);
                if (freeBound == null || freeBound - targetNorm >= TARGET_CONVERT_MAX_CORRIDOR) {
                    targetNorm = Double.NaN;
                } else {
                    searchSpec = new JumpSpec(sc, withoutTargetWalls(spec, max), spec.objective);
                }
            }
            if (SolverTrace.on()) {
                SolverTrace.log("BNB", "start n=%d m=%d budgetMs=%d stopAt=%s target=%s",
                        sc.numTicks, spec.constraints.size(), budgetNanos / 1_000_000L,
                        Double.isNaN(stopAtObjective) ? "-" : SolverTrace.fmt("%.6f", stopAtObjective),
                        Double.isNaN(targetNorm) ? "-" : SolverTrace.fmt("%.6f", targetNorm));
            }
            List<Pattern> patterns = enumeratePatterns(exact, searchSpec, sc, cfg.maxPatterns, searchCancel);
            if (patterns.isEmpty()) {
                if (DEBUG) System.out.println("  BNB no viable pattern (trivial infeasible)");
                if (SolverTrace.on()) SolverTrace.log("BNB", "no viable pattern (trivial infeasible)");
                return null;
            }
            if (SolverTrace.on()) {
                for (Pattern p : patterns) SolverTrace.log("BNB", "pattern %s bound=%.9f", p.label, p.normBound);
            }
            double stopNorm = Double.isNaN(stopAtObjective) ? Double.NaN
                    : (max ? stopAtObjective : -stopAtObjective);
            JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
            double[] incumbentYaws = ClosedFormSolve.optimize(exact, spec, feasTol, searchCancel);
            double incumbentNorm = normIfFeasible(exact, sc, compiled, spec, max, incumbentYaws);
            double[] rankYaws = incumbentYaws;
            if (warmIncumbent != null) {
                double[] wy = Angles.wrapAll(warmIncumbent);
                double wn = normIfFeasible(exact, sc, compiled, spec, max, wy);
                if (!Double.isNaN(wn) && (Double.isNaN(incumbentNorm) || wn > incumbentNorm)) {
                    incumbentNorm = wn;
                    incumbentYaws = wy;
                    rankYaws = wy;
                }
            }
            if (Double.isNaN(incumbentNorm)) {
                incumbentYaws = null;
                incumbentNorm = Double.NEGATIVE_INFINITY;
                if (!Double.isNaN(targetNorm) && !searchCancel.get()) {
                    rankYaws = ClosedFormSolve.optimize(exact, searchSpec, feasTol, searchCancel);
                }
            }
            if (!searchCancel.get()) {
                JumpLinearModel free = new JumpLinearModel(sc);
                double[] gateRef = rankYaws != null ? rankYaws : freeDualSeed(searchSpec, free);
                List<Pattern> keepAlive = keepAlivePatterns(exact, searchSpec, sc, free, gateRef);
                if (SolverTrace.on()) {
                    for (Pattern p : keepAlive) SolverTrace.log("BNB", "pattern %s bound=%.9f", p.label, p.normBound);
                }
                patterns.addAll(keepAlive);
            }
            for (Pattern p : patterns) {
                if (!p.patterned || p.zeroX == null || searchCancel.get()) continue;
                double[] py = ClosedFormSolve.optimizeWithPattern(exact, spec, feasTol, searchCancel, p.zeroX, p.zeroZ);
                double pn = normIfFeasible(exact, sc, compiled, spec, max, py);
                if (!Double.isNaN(pn) && pn > incumbentNorm) {
                    incumbentNorm = pn;
                    incumbentYaws = Angles.wrapAll(py);
                    if (rankYaws == null) rankYaws = incumbentYaws;
                    if (SolverTrace.on()) SolverTrace.log("BNB", "banded incumbent %s=%.9f", p.label, pn);
                }
            }
            if (!Double.isNaN(stopNorm) && incumbentNorm >= stopNorm) return incumbentYaws;
            double floorNorm = Double.isNaN(targetNorm) ? Double.NEGATIVE_INFINITY : targetNorm - cfg.pruneTol;
            final double seedNorm = Math.max(incumbentNorm, floorNorm);
            final double[] seedYaws = incumbentYaws;
            final double[] profileYaws = rankYaws;
            List<Pattern> viable = new ArrayList<>();
            for (Pattern p : patterns) {
                if (p.normBound > seedNorm + cfg.pruneTol) {
                    viable.add(p);
                } else {
                    if (DEBUG) System.out.printf("  BNB pattern %s pruned (bound=%.6f)%n", p.label, p.normBound);
                    if (SolverTrace.on()) SolverTrace.log("BNB", "pattern %s pruned at root (bound=%.9f <= seed=%.9f)", p.label, p.normBound, seedNorm);
                }
            }
            if (profileYaws != null && viable.size() > 1) {
                double[][] carry = carryProfile(new JumpLinearModel(sc), sc, Angles.wrapAll(profileYaws));
                double thr = exact.inertiaThreshold();
                boolean perAxis = exact.perAxisInertia();
                int[] bandMin = new int[2];
                for (int a = 0; a < 2; a++) {
                    int last = -1;
                    for (int t = 0; t < carry[a].length; t++) if (carry[a][t] > thr) last = t;
                    bandMin[a] = last + 1;
                }
                viable.sort((a, b) -> {
                    boolean ai = inBand(a, bandMin, perAxis);
                    boolean bi = inBand(b, bandMin, perAxis);
                    if (ai != bi) return ai ? -1 : 1;
                    return Double.compare(b.normBound, a.normBound);
                });
                if (SolverTrace.on()) {
                    for (Pattern p : viable) {
                        SolverTrace.log("BNB", "rank %s band=%s bound=%.9f", p.label, inBand(p, bandMin, perAxis), p.normBound);
                    }
                }
            }
            if (!viable.isEmpty() && !searchCancel.get()) {
                int threads = Math.min(viable.size(), Math.max(2, Runtime.getRuntime().availableProcessors() - 2));
                final long sliceNanos = viable.size() > threads
                        ? Math.max(1L, (searchDeadline - System.nanoTime()) * threads / viable.size())
                        : 0L;
                if (SolverTrace.on() && sliceNanos > 0L) {
                    SolverTrace.log("BNB", "slicing %d patterns over %d threads, sliceMs=%d",
                            viable.size(), threads, sliceNanos / 1_000_000L);
                }
                java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads, r -> {
                    Thread t = new Thread(r, "bnb-pattern");
                    t.setDaemon(true);
                    return t;
                });
                try {
                    List<java.util.concurrent.Future<Search>> futures = new ArrayList<>();
                    final double searchFloor = floorNorm;
                    final JumpSpec treeSpec = searchSpec;
                    for (Pattern p : viable) {
                        futures.add(pool.submit(() -> {
                            if (searchCancel.get()) return null;
                            long deadline = sliceNanos > 0L
                                    ? Math.min(searchDeadline, System.nanoTime() + sliceNanos)
                                    : searchDeadline;
                            Search search = Search.build(exact, treeSpec, compiled, feasTol, searchCancel, deadline,
                                    p.lin, p.velWalls, p.patterned, stopNorm, p.label, searchFloor, cfg, miss);
                            if (search == null) return null;
                            if (seedYaws != null) search.offer(seedYaws);
                            search.run();
                            return search;
                        }));
                    }
                    for (int i = 0; i < futures.size(); i++) {
                        Search search;
                        try {
                            long wait = Math.max(1L, searchDeadline - System.nanoTime() + 2_000_000_000L);
                            search = futures.get(i).get(wait, java.util.concurrent.TimeUnit.NANOSECONDS);
                        } catch (java.util.concurrent.TimeoutException e) {
                            continue;
                        } catch (Exception e) {
                            e.printStackTrace();
                            continue;
                        }
                        if (search == null) continue;
                        if (DEBUG) {
                            System.out.printf("  BNB pattern %s bound=%.6f nodes=%d pruned=%d restoreHits=%d slp=%d slpHits=%d incumbent=%s%n",
                                    viable.get(i).label, viable.get(i).normBound, search.statNodes, search.statPruned,
                                    search.statRestoreHits, search.statSlpCalls, search.statSlpHits,
                                    search.incumbentYaws == null ? "none" : String.valueOf(search.incumbentNorm));
                        }
                        if (SolverTrace.on()) {
                            SolverTrace.log("BNB", "pattern %s done bound=%.9f nodes=%d pruned=%d restoreHits=%d slp=%d slpHits=%d incumbent=%s",
                                    viable.get(i).label, viable.get(i).normBound, search.statNodes, search.statPruned,
                                    search.statRestoreHits, search.statSlpCalls, search.statSlpHits,
                                    search.incumbentYaws == null ? "none" : String.valueOf(search.incumbentNorm));
                        }
                        if (search.incumbentYaws != null && search.incumbentNorm > incumbentNorm) {
                            incumbentYaws = search.incumbentYaws;
                            incumbentNorm = search.incumbentNorm;
                        }
                    }
                } finally {
                    pool.shutdownNow();
                }
            }
            if (incumbentYaws != null && !polishCancel.get()) {
                double[] polished = SlpSolve.optimize(exact, spec, feasTol, polishCancel, incumbentYaws,
                        cfg.polishSlpPhase1Calls, cfg.polishSlpTotalCalls);
                double polishedNorm = normIfFeasible(exact, sc, compiled, spec, max, polished);
                if (!Double.isNaN(polishedNorm) && polishedNorm > incumbentNorm) {
                    incumbentYaws = Angles.wrapAll(polished);
                    incumbentNorm = polishedNorm;
                }
            }
            if (SolverTrace.on()) {
                SolverTrace.log("BNB", "end incumbent=%s ms=%d",
                        incumbentYaws == null ? "none" : SolverTrace.fmt("%.9f", incumbentNorm),
                        (System.nanoTime() - start) / 1_000_000L);
            }
            return incumbentYaws;
        } finally {
            finished.set(true);
            watchdog.interrupt();
        }
    }

    private static boolean isTargetWall(JumpConstraint c, JumpSpec spec, boolean max) {
        JumpConstraint.Mode objMode = spec.objective.axis == JumpPhysicsInputs.Axis.X
                ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z;
        return c.mode == objMode && c.t1 == spec.objective.tick && c.t2 == null
                && c.op == JumpConstraint.Op.PLUS
                && c.cmp == (max ? JumpConstraint.Cmp.GE : JumpConstraint.Cmp.LE);
    }

    private static double targetWallNorm(JumpSpec spec, boolean max) {
        double target = Double.NaN;
        for (JumpConstraint c : spec.constraints) {
            if (!isTargetWall(c, spec, max)) continue;
            double norm = max ? c.rhs : -c.rhs;
            if (Double.isNaN(target) || norm > target) target = norm;
        }
        return target;
    }

    private static List<JumpConstraint> withoutTargetWalls(JumpSpec spec, boolean max) {
        List<JumpConstraint> out = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) {
            if (!isTargetWall(c, spec, max)) out.add(c);
        }
        return out;
    }

    private static double normIfFeasible(ExactJumpModel exact, JumpPhysicsInputs sc,
                                         JumpConstraintCompiler.Compiled compiled, JumpSpec spec,
                                         boolean max, double[] yawsAbs) {
        if (yawsAbs == null) return Double.NaN;
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        ForwardPath path = exact.forward(sc, gf);
        if (compiled.maxViolation(gf, path) > 0.0) return Double.NaN;
        double v = path.getPos(spec.objective.tick, spec.objective.axis);
        return max ? v : -v;
    }

    private static final class Pattern {
        final String label;
        final JumpLinearModel lin;
        final List<JumpLinearModel.Wall> velWalls;
        final boolean patterned;
        final double normBound;
        final int zeroFrom;
        final int zeroAxis;
        final boolean[] zeroX;
        final boolean[] zeroZ;

        Pattern(String label, JumpLinearModel lin, List<JumpLinearModel.Wall> velWalls,
                boolean patterned, double normBound, int zeroFrom, int zeroAxis,
                boolean[] zeroX, boolean[] zeroZ) {
            this.label = label;
            this.lin = lin;
            this.velWalls = velWalls;
            this.patterned = patterned;
            this.normBound = normBound;
            this.zeroFrom = zeroFrom;
            this.zeroAxis = zeroAxis;
            this.zeroX = zeroX;
            this.zeroZ = zeroZ;
        }
    }

    private static double[][] carryProfile(JumpLinearModel free, JumpPhysicsInputs sc, double[] yawsAbs) {
        int n = free.n;
        double[][] out = new double[2][n];
        double vx = sc.initialVelocity.x;
        double vz = sc.initialVelocity.z;
        for (int t = 0; t < n; t++) {
            out[0][t] = Math.abs(vx);
            out[1][t] = Math.abs(vz);
            double phi = free.baseArg(t) + yawsAbs[t] * RAD;
            vx += free.mMag(t) * Math.cos(phi);
            vz += free.mMag(t) * Math.sin(phi);
            vx *= free.friction(t);
            vz *= free.friction(t);
        }
        return out;
    }

    private static boolean inBand(Pattern p, int[] bandMin, boolean perAxis) {
        if (p.zeroFrom < 0) return false;
        if (perAxis) return p.zeroFrom >= bandMin[p.zeroAxis];
        return p.zeroFrom >= bandMin[0] && p.zeroFrom >= bandMin[1];
    }

    private static List<Pattern> enumeratePatterns(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                                   int maxPatterns, AtomicBoolean cancel) {
        int n = sc.numTicks;
        List<Pattern> out = new ArrayList<>();
        JumpLinearModel free = new JumpLinearModel(sc);
        Double freeBound = rootBound(spec, free, null);
        if (freeBound != null) {
            out.add(new Pattern("free", free, new ArrayList<JumpLinearModel.Wall>(), false, freeBound, -1, -1, null, null));
        }
        double thr = exact.inertiaThreshold();
        boolean perAxis = exact.perAxisInertia();
        List<Pattern> cands = new ArrayList<>();
        for (int k = 1; k < n; k++) {
            if (cancel != null && cancel.get()) break;
            if (perAxis) {
                addPattern(cands, spec, sc, thr, k, n, true, false, "zx@" + k);
                addPattern(cands, spec, sc, thr, k, n, false, true, "zz@" + k);
                if (k < n - 1) {
                    addPattern(cands, spec, sc, thr, k, k + 1, true, false, "zx1@" + k);
                    addPattern(cands, spec, sc, thr, k, k + 1, false, true, "zz1@" + k);
                }
            } else {
                addPattern(cands, spec, sc, thr, k, n, true, true, "zxz@" + k);
                if (k < n - 1) addPattern(cands, spec, sc, thr, k, k + 1, true, true, "zxz1@" + k);
            }
        }
        cands.sort((a, b) -> Double.compare(b.normBound, a.normBound));
        for (int i = 0; i < cands.size() && i < maxPatterns; i++) out.add(cands.get(i));
        out.sort((a, b) -> Double.compare(b.normBound, a.normBound));
        return out;
    }

    private static void addPattern(List<Pattern> cands, JumpSpec spec, JumpPhysicsInputs sc,
                                   double thr, int k, int end, boolean zx, boolean zz, String label) {
        int n = sc.numTicks;
        boolean[] zeroX = new boolean[n];
        boolean[] zeroZ = new boolean[n];
        for (int t = k; t < end; t++) {
            if (zx) zeroX[t] = true;
            if (zz) zeroZ[t] = true;
        }
        JumpLinearModel lin = new JumpLinearModel(sc, zeroX, zeroZ);
        List<JumpLinearModel.Wall> vel = lin.velocityWalls(thr);
        if (vel.isEmpty()) return;
        Double bound = rootBound(spec, lin, vel);
        if (bound == null) return;
        cands.add(new Pattern(label, lin, vel, true, bound, k, zx && zz ? 0 : (zx ? 0 : 1), zeroX, zeroZ));
    }

    /** Patterns that hold the objective axis out of the inertia band at a momentum reversal, the branch
     *  complementary to the zeroing patterns: the gate destroys the whole carry when the axis coasts through
     *  the band, and the free relaxation cannot see that because it never models the gate at all. Nominated
     *  where {@code reference} either trips the gate or reverses sign, in the sign the objective improves. */
    private static List<Pattern> keepAlivePatterns(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                                   JumpLinearModel free, double[] reference) {
        List<Pattern> out = new ArrayList<>();
        if (reference == null) return out;
        int axis = spec.objective.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;
        boolean positive = spec.objective.sense == Objective.Sense.MAX;
        ForwardPath path = exact.forward(sc, sc.toGameFacings(Angles.wrapAll(reference)));
        double[] vel = axis == 0 ? path.velX : path.velZ;
        if (vel == null) return out;
        double thr = exact.inertiaThreshold();
        for (int k = 1; k < free.n && out.size() < MAX_KEEP_ALIVE; k++) {
            if (Math.abs(vel[k]) >= thr && vel[k - 1] * vel[k] >= 0.0) continue;
            JumpLinearModel.Wall w = free.keepAliveWall(axis, k, thr, positive);
            if (w == null) continue;
            List<JumpLinearModel.Wall> velWalls = new ArrayList<>(1);
            velWalls.add(w);
            Double bound = rootBound(spec, free, velWalls);
            if (bound == null) continue;
            out.add(new Pattern(w.name, free, velWalls, true, bound, -1, -1, null, null));
        }
        return out;
    }

    /** The margin-0 dual recovery of the unpatterned model: the always-available reference trajectory when
     *  the closed form found nothing to rank by. */
    private static double[] freeDualSeed(JumpSpec spec, JumpLinearModel lin) {
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivial);
        if (trivial[0]) return null;
        int n = lin.n;
        double[] cx = new double[n];
        double[] cz = new double[n];
        lin.objectiveVectors(spec.objective, cx, cz);
        CostateDualSolver.Result r = new CostateDualSolver(n, cx, cz, lin.mMagAll(), walls).solve(0.0, null);
        if (r == null) return null;
        return lin.recoverAlongCostate(spec.objective, r.gx, r.gz);
    }

    private static Double rootBound(JumpSpec spec, JumpLinearModel lin, List<JumpLinearModel.Wall> vel) {
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivial);
        if (trivial[0]) return null;
        if (vel != null) walls.addAll(vel);
        int n = lin.n;
        double[] cx = new double[n];
        double[] cz = new double[n];
        lin.objectiveVectors(spec.objective, cx, cz);
        CostateDualSolver.Result r = new CostateDualSolver(n, cx, cz, lin.mMagAll(), walls).solve(0.0, null);
        if (r == null) return null;
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        int axisIdx = spec.objective.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;
        double cp = lin.constPos(spec.objective.tick, axisIdx);
        double bound = r.value + (max ? cp : -cp);
        return Double.isNaN(bound) ? null : bound;
    }

    private static Thread startWatchdog(AtomicBoolean outer, AtomicBoolean finished,
                                        AtomicBoolean searchCancel, long searchDeadline,
                                        AtomicBoolean polishCancel, long fullDeadline) {
        Thread t = new Thread(() -> {
            while (!finished.get()) {
                boolean stop = outer.get();
                long now = System.nanoTime();
                if (stop || now >= searchDeadline) searchCancel.set(true);
                if (stop || now >= fullDeadline) polishCancel.set(true);
                if (polishCancel.get()) return;
                try {
                    Thread.sleep(2L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "bound-pruned-recovery-watchdog");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static final class Node {
        final double[] lo;
        final double[] hi;
        final double[] lambda;
        final double bound;
        final double[] seamPos;
        final double[] seed;
        final double seedViol;
        final int depth;
        final long order;

        Node(double[] lo, double[] hi, double[] lambda, double bound, double[] seamPos,
             double[] seed, double seedViol, int depth, long order) {
            this.lo = lo;
            this.hi = hi;
            this.lambda = lambda;
            this.bound = bound;
            this.seamPos = seamPos;
            this.seed = seed;
            this.seedViol = seedViol;
            this.depth = depth;
            this.order = order;
        }
    }

    private static final class Restored {
        final double[] theta;
        final double viol;
        final ForwardPath path;

        Restored(double[] theta, double viol, ForwardPath path) {
            this.theta = theta;
            this.viol = viol;
            this.path = path;
        }
    }

    private static final class Search {
        final ExactJumpModel exact;
        final JumpSpec spec;
        final double feasTol;
        final AtomicBoolean cancel;
        final long deadline;
        final JumpPhysicsInputs sc;
        final JumpLinearModel lin;
        final JumpConstraintCompiler.Compiled compiled;
        final double[] cx;
        final double[] cz;
        final double[] mMag;
        final boolean max;
        final double normConst;
        final List<JumpConstraint> canonical;
        final List<JumpLinearModel.Wall> baseWalls;
        final int[] consOfWall;
        final int seamCount;
        final int[] seamTick;
        final int[] seamAxis;
        final int[] seamGeWall;
        final int[] seamLeWall;
        final int[] seamGeCons;
        final int[] seamLeCons;
        final double[] seamConst;
        final double[] baseLo;
        final double[] baseHi;
        final PriorityQueue<Node> open;
        long seq;
        double[] incumbentYaws;
        double incumbentNorm = Double.NEGATIVE_INFINITY;
        boolean traceRestore;
        int statNodes;
        int statPruned;
        int statRestoreHits;
        int statSlpCalls;
        int statSlpHits;

        final int consWallCount;
        final boolean patterned;
        final double stopNorm;
        final String label;
        final Config cfg;
        final ClosestMiss miss;
        int lastRestoreIters;

        private Search(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel, long deadline,
                       JumpPhysicsInputs sc, JumpLinearModel lin, JumpConstraintCompiler.Compiled compiled,
                       double[] cx, double[] cz, List<JumpConstraint> canonical, List<JumpLinearModel.Wall> baseWalls,
                       int[] consOfWall, int[] seamTick, int[] seamAxis, int[] seamGeWall, int[] seamLeWall,
                       int[] seamGeCons, int[] seamLeCons, double[] seamConst, double[] baseLo, double[] baseHi,
                       int consWallCount, boolean patterned, double stopNorm, String label, double floorNorm,
                       Config cfg, ClosestMiss miss) {
            this.consWallCount = consWallCount;
            this.patterned = patterned;
            this.stopNorm = stopNorm;
            this.label = label;
            this.cfg = cfg;
            this.miss = miss;
            this.incumbentNorm = floorNorm;
            this.exact = exact;
            this.spec = spec;
            this.feasTol = feasTol;
            this.cancel = cancel;
            this.deadline = deadline;
            this.sc = sc;
            this.lin = lin;
            this.compiled = compiled;
            this.cx = cx;
            this.cz = cz;
            this.mMag = lin.mMagAll();
            this.max = spec.objective.sense == Objective.Sense.MAX;
            int axisIdx = spec.objective.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;
            double cp = lin.constPos(spec.objective.tick, axisIdx);
            this.normConst = max ? cp : -cp;
            this.canonical = canonical;
            this.baseWalls = baseWalls;
            this.consOfWall = consOfWall;
            this.seamCount = seamTick.length;
            this.seamTick = seamTick;
            this.seamAxis = seamAxis;
            this.seamGeWall = seamGeWall;
            this.seamLeWall = seamLeWall;
            this.seamGeCons = seamGeCons;
            this.seamLeCons = seamLeCons;
            this.seamConst = seamConst;
            this.baseLo = baseLo;
            this.baseHi = baseHi;
            this.open = new PriorityQueue<>((a, b) -> {
                int c = Long.compare(quantizedBound(b.bound), quantizedBound(a.bound));
                if (c != 0) return c;
                if (a.depth != b.depth) return Integer.compare(b.depth, a.depth);
                return Long.compare(a.order, b.order);
            });
        }

        static Search build(ExactJumpModel exact, JumpSpec spec, JumpConstraintCompiler.Compiled acceptCompiled,
                            double feasTol, AtomicBoolean cancel, long deadline,
                            JumpLinearModel lin, List<JumpLinearModel.Wall> velWalls, boolean patterned,
                            double stopNorm, String label, double floorNorm, Config cfg, ClosestMiss miss) {
            JumpPhysicsInputs sc = spec.asScenario();
            int objTick = spec.objective.tick;

            TreeMap<Integer, Double> geMax = new TreeMap<>();
            TreeMap<Integer, Double> leMin = new TreeMap<>();
            for (JumpConstraint c : spec.constraints) {
                Integer key = seamKey(c, objTick);
                if (key == null) continue;
                if (c.cmp == JumpConstraint.Cmp.GE) {
                    Double cur = geMax.get(key);
                    if (cur == null || c.rhs > cur) geMax.put(key, c.rhs);
                } else {
                    Double cur = leMin.get(key);
                    if (cur == null || c.rhs < cur) leMin.put(key, c.rhs);
                }
            }
            TreeMap<Integer, double[]> bands = new TreeMap<>();
            for (Integer key : geMax.keySet()) {
                int tick = key >> 1;
                int axis = key & 1;
                double lo = geMax.get(key);
                Double le = leMin.get(key);
                double hi = le != null ? le : lin.constPos(tick, axis) + reach(lin, tick) + SYNTH_PAD;
                if (hi > lo) bands.put(key, new double[]{lo, hi});
            }
            for (Integer key : leMin.keySet()) {
                if (bands.containsKey(key) || geMax.containsKey(key)) continue;
                int tick = key >> 1;
                int axis = key & 1;
                double hi = leMin.get(key);
                double lo = lin.constPos(tick, axis) - reach(lin, tick) - SYNTH_PAD;
                if (hi > lo) bands.put(key, new double[]{lo, hi});
            }

            List<JumpConstraint> canonical = new ArrayList<>();
            for (JumpConstraint c : spec.constraints) {
                Integer key = seamKey(c, objTick);
                if (key != null && bands.containsKey(key)) continue;
                canonical.add(c);
            }
            List<Integer> seamKeys = new ArrayList<>(bands.keySet());
            int[] geCons = new int[seamKeys.size()];
            int[] leCons = new int[seamKeys.size()];
            for (int i = 0; i < seamKeys.size(); i++) {
                int key = seamKeys.get(i);
                int tick = key >> 1;
                double[] band = bands.get(key);
                JumpConstraint.Mode mode = (key & 1) == 0 ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z;
                String tag = mode + "@" + tick + ".seam";
                geCons[i] = canonical.size();
                canonical.add(new JumpConstraint(mode, tick, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE,
                        band[0], tag + "Lo"));
                leCons[i] = canonical.size();
                canonical.add(new JumpConstraint(mode, tick, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE,
                        band[1], tag + "Hi"));
            }

            boolean[] trivial = {false};
            List<JumpLinearModel.Wall> walls = new ArrayList<>();
            List<Integer> consOf = new ArrayList<>();
            int[] wallOf = new int[canonical.size()];
            for (int i = 0; i < canonical.size(); i++) {
                JumpLinearModel.Wall w = lin.compileWall(canonical.get(i), 0.0, trivial);
                if (trivial[0]) return null;
                wallOf[i] = w == null ? -1 : walls.size();
                if (w != null) {
                    walls.add(w);
                    consOf.add(i);
                }
            }
            int[] consOfWall = new int[consOf.size()];
            for (int i = 0; i < consOf.size(); i++) consOfWall[i] = consOf.get(i);

            List<int[]> meta = new ArrayList<>();
            for (int i = 0; i < seamKeys.size(); i++) {
                int gw = wallOf[geCons[i]];
                int lw = wallOf[leCons[i]];
                if (gw < 0 || lw < 0) continue;
                meta.add(new int[]{seamKeys.get(i), gw, lw, geCons[i], leCons[i]});
            }
            int sCount = meta.size();
            int[] seamTick = new int[sCount];
            int[] seamAxis = new int[sCount];
            int[] seamGeWall = new int[sCount];
            int[] seamLeWall = new int[sCount];
            int[] seamGeCons = new int[sCount];
            int[] seamLeCons = new int[sCount];
            double[] seamConst = new double[sCount];
            double[] baseLo = new double[sCount];
            double[] baseHi = new double[sCount];
            for (int i = 0; i < sCount; i++) {
                int[] row = meta.get(i);
                int key = row[0];
                seamTick[i] = key >> 1;
                seamAxis[i] = key & 1;
                seamGeWall[i] = row[1];
                seamLeWall[i] = row[2];
                seamGeCons[i] = row[3];
                seamLeCons[i] = row[4];
                seamConst[i] = lin.constPos(seamTick[i], seamAxis[i]);
                double[] band = bands.get(key);
                baseLo[i] = band[0];
                baseHi[i] = band[1];
            }

            int consWallCount = walls.size();
            if (velWalls != null) walls.addAll(velWalls);
            double[] cx = new double[lin.n];
            double[] cz = new double[lin.n];
            lin.objectiveVectors(spec.objective, cx, cz);
            return new Search(exact, spec, feasTol, cancel, deadline, sc, lin, acceptCompiled, cx, cz, canonical, walls,
                    consOfWall, seamTick, seamAxis, seamGeWall, seamLeWall, seamGeCons, seamLeCons, seamConst,
                    baseLo, baseHi, consWallCount, patterned, stopNorm, label, floorNorm, cfg, miss);
        }

        private static double reach(JumpLinearModel lin, int tick) {
            double r = 0.0;
            for (int s = 0; s < tick; s++) r += lin.coef(s, tick) * lin.mMag(s);
            return r;
        }

        private static Integer seamKey(JumpConstraint c, int objTick) {
            if (c.mode == JumpConstraint.Mode.F || c.t2 != null || c.op != JumpConstraint.Op.PLUS) return null;
            if (c.cmp == JumpConstraint.Cmp.EQ) return null;
            if (c.t1 < 1 || c.t1 >= objTick) return null;
            return c.t1 * 2 + (c.mode == JumpConstraint.Mode.X ? 0 : 1);
        }

        void run() {
            Node root = makeNode(baseLo.clone(), baseHi.clone(), null, 0);
            if (root == null) return;
            open.add(root);
            while (!open.isEmpty()) {
                if (expired()) return;
                Node nd = open.poll();
                if (nd.bound <= incumbentNorm + cfg.pruneTol) {
                    statPruned++;
                    continue;
                }
                int si = chooseSeam(nd);
                if (nd.depth == 0 || si < 0 || nd.seedViol <= cfg.slpViolTrigger) {
                    if (!expired()) {
                        statSlpCalls++;
                        if (DEBUG && statSlpCalls <= 12) {
                            System.out.printf("  BNB slp#%d depth=%d viol=%.5f bound=%.6f%n",
                                    statSlpCalls, nd.depth, nd.seedViol, nd.bound);
                        }
                        if (SolverTrace.on()) {
                            SolverTrace.log("BNB", "%s slp#%d depth=%d seedViol=%.3e bound=%.9f", label, statSlpCalls, nd.depth, nd.seedViol, nd.bound);
                        }
                        double[] slp = patterned
                                ? SlpSolve.optimizeBestEffort(exact, pinnedSpec(nd.lo, nd.hi), feasTol, cancel, nd.seed,
                                        cfg.treeSlpPhase1Calls, cfg.treeSlpTotalCalls, true, cfg.treeSlpTrMinDeg)
                                : SlpSolve.optimize(exact, pinnedSpec(nd.lo, nd.hi), feasTol, cancel, nd.seed,
                                        cfg.treeSlpPhase1Calls, cfg.treeSlpTotalCalls);
                        if (slp != null && !Double.isNaN(offer(slp))) statSlpHits++;
                    }
                    if (nd.bound <= incumbentNorm + cfg.pruneTol) continue;
                }
                if (si < 0) continue;
                double w = nd.hi[si] - nd.lo[si];
                double cut = Math.max(nd.lo[si] + 0.3 * w, Math.min(nd.hi[si] - 0.3 * w, nd.seamPos[si]));
                addChild(nd, si, nd.lo[si], cut);
                if (expired()) return;
                addChild(nd, si, cut, nd.hi[si]);
            }
        }

        private void addChild(Node parent, int si, double lo, double hi) {
            double[] clo = parent.lo.clone();
            double[] chi = parent.hi.clone();
            clo[si] = lo;
            chi[si] = hi;
            Node child = makeNode(clo, chi, parent.lambda, parent.depth + 1);
            if (child != null) open.add(child);
        }

        private Node makeNode(double[] lo, double[] hi, double[] warm, int depth) {
            statNodes++;
            List<JumpLinearModel.Wall> nodeWalls = nodeWalls(lo, hi);
            CostateDualSolver dual = new CostateDualSolver(lin.n, cx, cz, mMag, nodeWalls);
            CostateDualSolver.Result r0 = dual.solve(0.0, warm);
            if (r0 == null) {
                statPruned++;
                if (SolverTrace.on()) SolverTrace.log("BNB", "%s node#%d depth=%d dual unbounded, pruned", label, statNodes, depth);
                return null;
            }
            double bound = r0.value + normConst;
            if (Double.isNaN(bound) || bound <= incumbentNorm + cfg.pruneTol) {
                statPruned++;
                if (SolverTrace.on()) SolverTrace.log("BNB", "%s node#%d depth=%d bound=%.9f pruned vs incumbent=%.9f", label, statNodes, depth, bound, incumbentNorm);
                return null;
            }

            List<JumpConstraint> aligned = nodeAlignedCons(lo, hi);
            double[] slackScratch = new double[nodeWalls.size()];
            double[] residScratch = new double[nodeWalls.size()];
            double[] bestSeed = recover(r0);
            double bestSeedViol = seedViolOf(aligned, nodeWalls, bestSeed, slackScratch, residScratch);
            if (bestSeedViol > feasTol) {
                double[] rungWarm = r0.lambda;
                for (double margin : cfg.seedMargins) {
                    if (expired()) break;
                    CostateDualSolver.Result rm = dual.solve(margin, rungWarm);
                    if (rm == null) break;
                    rungWarm = rm.lambda;
                    double[] cand = recover(rm);
                    double v = seedViolOf(aligned, nodeWalls, cand, slackScratch, residScratch);
                    if (v < bestSeedViol) {
                        bestSeedViol = v;
                        bestSeed = cand;
                    }
                    if (v <= feasTol) break;
                }
            }
            traceRestore = DEBUG && depth == 0;
            Restored rest = restore(aligned, nodeWalls, bestSeed);
            traceRestore = false;
            if (SolverTrace.on()) {
                SolverTrace.log("BNB", "%s node#%d depth=%d bound=%.9f seedViol=%.3e restoreViol=%.3e restoreIters=%d",
                        label, statNodes, depth, bound, bestSeedViol, rest.viol, lastRestoreIters);
            }
            if (rest.viol <= feasTol && !Double.isNaN(offer(rest.theta))) statRestoreHits++;
            if (DEBUG && depth == 0 && rest.viol > feasTol) {
                double[] dgf = sc.toGameFacings(rest.theta);
                for (int j = 0; j < aligned.size(); j++) {
                    JumpConstraint c = aligned.get(j);
                    double s = JumpConstraintCompiler.slack(c, dgf, rest.path);
                    if (s > 0) System.out.printf("  BNB root residual %s slack=%.3e%n", c.name, s);
                }
            }

            double[] seamPos = new double[seamCount];
            for (int i = 0; i < seamCount; i++) {
                seamPos[i] = seamAxis[i] == 0 ? rest.path.posX[seamTick[i]] : rest.path.posZ[seamTick[i]];
            }
            if (bound <= incumbentNorm + cfg.pruneTol) {
                statPruned++;
                return null;
            }
            return new Node(lo, hi, r0.lambda, bound, seamPos, rest.theta, rest.viol, depth, seq++);
        }

        private Restored restore(List<JumpConstraint> cons, List<JumpLinearModel.Wall> walls, double[] seed) {
            int n = lin.n;
            int m = walls.size();
            lastRestoreIters = 0;
            double[] theta = Angles.wrapAll(seed);
            double[] gf = sc.toGameFacings(theta);
            ForwardPath path = exact.forward(sc, gf);
            double[] slack = new double[m];
            double[] resid = new double[m];
            double cur = slacks(cons, walls, theta, gf, path, slack, resid);
            double[] bestTheta = theta;
            double bestViol = cur;
            ForwardPath bestPath = path;
            if (m == 0 || cur <= feasTol) return new Restored(bestTheta, bestViol, bestPath);

            double damp = 1.0e-3;
            double[] ux = new double[n];
            double[] uz = new double[n];
            double[][] a = new double[n][n];
            double[] b = new double[n];
            double[] d = new double[n];
            for (int iter = 0; iter < cfg.restoreIters; iter++) {
                if (expired()) break;
                lastRestoreIters = iter + 1;
                for (int t = 0; t < n; t++) {
                    double phi = lin.baseArg(t) + theta[t] * RAD;
                    ux[t] = lin.mMag(t) * Math.cos(phi);
                    uz[t] = lin.mMag(t) * Math.sin(phi);
                }
                for (int p = 0; p < n; p++) {
                    java.util.Arrays.fill(a[p], 0.0);
                    b[p] = 0.0;
                }
                double maxDiag = 0.0;
                boolean any = false;
                for (int j = 0; j < m; j++) {
                    JumpLinearModel.Wall w = walls.get(j);
                    boolean include = w.eq ? resid[j] != 0.0 : slack[j] > -RESTORE_ACTIVE_BAND;
                    if (!include) continue;
                    double r = resid[j];
                    if (r != 0.0) any = true;
                    for (int t = 0; t < n; t++) {
                        double jt = w.coef[t] * (w.axis == 0 ? -uz[t] : ux[t]) * RAD;
                        if (jt == 0.0) continue;
                        b[t] -= jt * r;
                        for (int t2 = t; t2 < n; t2++) {
                            double jt2 = w.coef[t2] * (w.axis == 0 ? -uz[t2] : ux[t2]) * RAD;
                            a[t][t2] += jt * jt2;
                        }
                    }
                }
                if (!any) break;
                for (int t = 0; t < n; t++) {
                    for (int t2 = 0; t2 < t; t2++) a[t][t2] = a[t2][t];
                    if (a[t][t] > maxDiag) maxDiag = a[t][t];
                }
                if (maxDiag <= 0.0) break;
                if (!cholesky(a, b, d, n, damp * maxDiag)) {
                    damp = Math.min(damp * 10.0, 1.0e6);
                    continue;
                }
                double step = 0.0;
                for (int t = 0; t < n; t++) step = Math.max(step, Math.abs(d[t]));
                if (step > RESTORE_STEP_CAP_DEG) {
                    double scale = RESTORE_STEP_CAP_DEG / step;
                    for (int t = 0; t < n; t++) d[t] *= scale;
                }
                double[] cand = new double[n];
                for (int t = 0; t < n; t++) cand[t] = Angles.wrap(theta[t] + d[t]);
                double[] cgf = sc.toGameFacings(cand);
                ForwardPath cpath = exact.forward(sc, cgf);
                double[] cSlack = new double[m];
                double[] cResid = new double[m];
                double cViol = slacks(cons, walls, cand, cgf, cpath, cSlack, cResid);
                double grow = 2.0;
                while (cViol < cur && step * grow <= RESTORE_STEP_CAP_DEG * 2.0) {
                    double[] cand2 = new double[n];
                    for (int t = 0; t < n; t++) cand2[t] = Angles.wrap(theta[t] + grow * d[t]);
                    double[] cgf2 = sc.toGameFacings(cand2);
                    ForwardPath cpath2 = exact.forward(sc, cgf2);
                    double[] cSlack2 = new double[m];
                    double[] cResid2 = new double[m];
                    double cViol2 = slacks(cons, walls, cand2, cgf2, cpath2, cSlack2, cResid2);
                    if (cViol2 >= cViol) break;
                    cand = cand2;
                    cgf = cgf2;
                    cpath = cpath2;
                    cSlack = cSlack2;
                    cResid = cResid2;
                    cViol = cViol2;
                    grow *= 2.0;
                }
                if (traceRestore) {
                    System.out.printf("    restore iter=%d cur=%.3e cand=%.3e step=%.4f damp=%.2e grow=%.1f%n",
                            iter, cur, cViol, step, damp, grow);
                }
                if (cViol < cur) {
                    theta = cand;
                    cur = cViol;
                    slack = cSlack;
                    resid = cResid;
                    path = cpath;
                    damp = Math.max(damp * 0.5, 1.0e-8);
                    if (cur < bestViol) {
                        bestViol = cur;
                        bestTheta = theta;
                        bestPath = path;
                    }
                    if (cur <= feasTol) break;
                } else {
                    damp = Math.min(damp * 10.0, 1.0e7);
                    if (damp >= 1.0e3) {
                        double[] probed = probeWorst(cons, walls, theta, slack, cur);
                        if (probed == null) break;
                        theta = probed;
                        gf = sc.toGameFacings(theta);
                        path = exact.forward(sc, gf);
                        cur = slacks(cons, walls, theta, gf, path, slack, resid);
                        damp = 1.0e-3;
                        if (cur < bestViol) {
                            bestViol = cur;
                            bestTheta = theta;
                            bestPath = path;
                        }
                        if (cur <= feasTol) break;
                    }
                }
            }
            return new Restored(bestTheta, bestViol, bestPath);
        }

        private double[] probeWorst(List<JumpConstraint> cons, List<JumpLinearModel.Wall> walls,
                                    double[] theta, double[] slack, double cur) {
            if (expired()) return null;
            int m = walls.size();
            int worst = -1;
            double worstSlack = 0.0;
            for (int j = 0; j < m; j++) {
                if (slack[j] > worstSlack) {
                    worstSlack = slack[j];
                    worst = j;
                }
            }
            if (worst < 0) return null;
            JumpLinearModel.Wall w = walls.get(worst);
            int n = lin.n;
            double maxInfl = 0.0;
            for (int t = 0; t < n; t++) {
                double infl = Math.abs(w.coef[t]) * lin.mMag(t);
                if (infl > maxInfl) maxInfl = infl;
            }
            if (maxInfl <= 0.0) return null;
            double[] deltas = {-4.0, -2.0, -1.0, -0.5, 0.5, 1.0, 2.0, 4.0};
            double[] scratchSlack = new double[m];
            double[] scratchResid = new double[m];
            double[] best = null;
            double bestV = cur - 1.0e-12;
            int probedTicks = 0;
            for (int t = 0; t < n && probedTicks < 6; t++) {
                double infl = Math.abs(w.coef[t]) * lin.mMag(t);
                if (infl < 0.25 * maxInfl) continue;
                probedTicks++;
                for (double delta : deltas) {
                    double[] cand = theta.clone();
                    cand[t] = Angles.wrap(cand[t] + delta);
                    double[] cgf = sc.toGameFacings(cand);
                    ForwardPath cpath = exact.forward(sc, cgf);
                    double v = slacks(cons, walls, cand, cgf, cpath, scratchSlack, scratchResid);
                    if (v < bestV) {
                        bestV = v;
                        best = cand;
                    }
                }
            }
            return best;
        }

        private double seedViolOf(List<JumpConstraint> cons, List<JumpLinearModel.Wall> walls, double[] theta,
                                  double[] slackScratch, double[] residScratch) {
            double[] wrapped = Angles.wrapAll(theta);
            double[] gf = sc.toGameFacings(wrapped);
            ForwardPath path = exact.forward(sc, gf);
            return slacks(cons, walls, wrapped, gf, path, slackScratch, residScratch);
        }

        private double slacks(List<JumpConstraint> cons, List<JumpLinearModel.Wall> walls, double[] theta,
                              double[] gf, ForwardPath path, double[] slackOut, double[] residOut) {
            double mx = 0.0;
            double[] ux = null;
            double[] uz = null;
            for (int j = 0; j < walls.size(); j++) {
                if (j < cons.size()) {
                    JumpConstraint c = cons.get(j);
                    double e = JumpConstraintCompiler.evaluate(c, gf, path);
                    if (walls.get(j).eq) {
                        slackOut[j] = Math.abs(e);
                        residOut[j] = e;
                        if (slackOut[j] > mx) mx = slackOut[j];
                    } else {
                        double s = c.cmp == JumpConstraint.Cmp.GE ? -e : e;
                        slackOut[j] = s;
                        residOut[j] = Math.max(0.0, s + RESTORE_AIM);
                        if (s > mx) mx = s;
                    }
                } else {
                    if (ux == null) {
                        int n = lin.n;
                        ux = new double[n];
                        uz = new double[n];
                        for (int t = 0; t < n; t++) {
                            double phi = lin.baseArg(t) + theta[t] * RAD;
                            ux[t] = lin.mMag(t) * Math.cos(phi);
                            uz[t] = lin.mMag(t) * Math.sin(phi);
                        }
                    }
                    JumpLinearModel.Wall w = walls.get(j);
                    double au = 0.0;
                    for (int t = 0; t < lin.n; t++) {
                        if (w.coef[t] == 0.0) continue;
                        au += w.coef[t] * (w.axis == 0 ? ux[t] : uz[t]);
                    }
                    double s = au - w.bPrime;
                    slackOut[j] = s;
                    residOut[j] = Math.max(0.0, s + RESTORE_AIM);
                    if (s > mx) mx = s;
                }
            }
            return mx;
        }

        private static boolean cholesky(double[][] a, double[] b, double[] out, int n, double dampAbs) {
            double[][] l = new double[n][n];
            if (!SpdCholesky.factor(a, l, n, dampAbs)) return false;
            System.arraycopy(b, 0, out, 0, n);
            SpdCholesky.solveInPlace(l, out, n);
            return true;
        }

        private int chooseSeam(Node nd) {
            int best = -1;
            double bestOut = -1.0;
            double bestWidth = -1.0;
            for (int i = 0; i < seamCount; i++) {
                double w = nd.hi[i] - nd.lo[i];
                if (w <= cfg.minSeamWidth) continue;
                double out = Math.max(0.0, Math.max(nd.lo[i] - nd.seamPos[i], nd.seamPos[i] - nd.hi[i]));
                if (best < 0 || out > bestOut || (out == bestOut && w > bestWidth)) {
                    best = i;
                    bestOut = out;
                    bestWidth = w;
                }
            }
            return best;
        }

        private List<JumpLinearModel.Wall> nodeWalls(double[] lo, double[] hi) {
            List<JumpLinearModel.Wall> walls = new ArrayList<>(baseWalls);
            for (int i = 0; i < seamCount; i++) {
                JumpLinearModel.Wall ge = baseWalls.get(seamGeWall[i]);
                JumpLinearModel.Wall le = baseWalls.get(seamLeWall[i]);
                walls.set(seamGeWall[i], new JumpLinearModel.Wall(ge.axis, ge.coef, seamConst[i] - lo[i], false, ge.name, ge.p0coef));
                walls.set(seamLeWall[i], new JumpLinearModel.Wall(le.axis, le.coef, hi[i] - seamConst[i], false, le.name, le.p0coef));
            }
            return walls;
        }

        private List<JumpConstraint> pinnedCons(double[] lo, double[] hi) {
            List<JumpConstraint> cons = new ArrayList<>(canonical);
            for (int i = 0; i < seamCount; i++) {
                JumpConstraint ge = canonical.get(seamGeCons[i]);
                JumpConstraint le = canonical.get(seamLeCons[i]);
                cons.set(seamGeCons[i], new JumpConstraint(ge.mode, ge.t1, null, JumpConstraint.Op.PLUS,
                        JumpConstraint.Cmp.GE, lo[i], ge.name));
                cons.set(seamLeCons[i], new JumpConstraint(le.mode, le.t1, null, JumpConstraint.Op.PLUS,
                        JumpConstraint.Cmp.LE, hi[i], le.name));
            }
            return cons;
        }

        private List<JumpConstraint> nodeAlignedCons(double[] lo, double[] hi) {
            List<JumpConstraint> pinned = pinnedCons(lo, hi);
            List<JumpConstraint> aligned = new ArrayList<>(consOfWall.length);
            for (int consIdx : consOfWall) aligned.add(pinned.get(consIdx));
            return aligned;
        }

        private JumpSpec pinnedSpec(double[] lo, double[] hi) {
            return new JumpSpec(sc, pinnedCons(lo, hi), spec.objective);
        }

        private double[] recover(CostateDualSolver.Result r) {
            return lin.recoverAlongCostate(spec.objective, r.gx, r.gz);
        }

        double offer(double[] yawsAbs) {
            double[] wrapped = Angles.wrapAll(yawsAbs);
            double[] gf = sc.toGameFacings(wrapped);
            ForwardPath path = exact.forward(sc, gf);
            double violation = compiled.maxViolation(gf, path);
            if (violation > feasTol) {
                if (miss != null) miss.offer(wrapped, violation);
                return Double.NaN;
            }
            double normed = normObjective(path);
            if (normed > incumbentNorm) {
                incumbentNorm = normed;
                incumbentYaws = wrapped;
                if (!Double.isNaN(stopNorm) && normed >= stopNorm) cancel.set(true);
                if (DEBUG) {
                    System.out.printf("  BNB incumbent %.9f at %.1f s (%s, %d nodes)%n",
                            normed, (System.nanoTime() - debugEpoch) / 1e9,
                            patterned ? "patterned" : "free", statNodes);
                }
                if (SolverTrace.on()) {
                    SolverTrace.log("BNB", "%s incumbent=%.9f nodes=%d%s", label, normed, statNodes,
                            !Double.isNaN(stopNorm) && normed >= stopNorm ? " stopAt reached" : "");
                }
            }
            return normed;
        }

        private double normObjective(ForwardPath path) {
            double v = path.getPos(spec.objective.tick, spec.objective.axis);
            return max ? v : -v;
        }

        private static long quantizedBound(double bound) {
            return (long) Math.floor(bound / BOUND_QUANTUM);
        }

        private boolean expired() {
            return cancel.get() || System.nanoTime() >= deadline;
        }
    }
}
