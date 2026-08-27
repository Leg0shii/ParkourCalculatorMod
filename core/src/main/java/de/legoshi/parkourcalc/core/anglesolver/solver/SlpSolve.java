package de.legoshi.parkourcalc.core.anglesolver.solver;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Trust-region sequential-LP solve on the byte-exact model: the post-failure closer for windows whose
 *  cross-seam wall coupling gives the Lagrangian dual a genuine duality gap. The problem stays exactly
 *  linear in the per-tick input vectors, so linearize {@code u_t(yaw)} around the current facings and
 *  LP-step them: phase 1 reduces the worst byte-exact wall violation, phase 2 improves the objective
 *  while staying strictly inside; the trust region shrinks on rejection. Seeded from the dual recovery
 *  at margin 0. Returns absolute wrapped feasible facings, or {@code null} (caller falls back). */
public final class SlpSolve {

    public static boolean DEBUG = false;

    /** Inward clearance required after phase 1 (~ the sine-bucket lattice spacing); phase 2 may hug back to a quarter. */
    private static final double CLEARANCE = 1.0e-6;
    private static final double RAD = Math.PI / 180.0;

    public static final class Config {
        /** Phase-1 share of the LP budget; not restoring feasibility within it means infeasible. */
        public int phase1Calls = 40;
        /** Total LP budget across both phases. */
        public int totalCalls = 60;
        public double trStartDeg = 30.0;
        public double trMaxDeg = 45.0;
        public double trMinDeg = 1.0e-7;
        public int lpMaxIter = 2000;
        /** Phase-1 target for a centered solve; the result is accepted at whatever clearance was reached
         *  (>= {@value SlpSolve#CLEARANCE}), so a corridor narrower than twice this still solves. */
        public double centerClearance = 2.0e-2;
    }

    private SlpSolve() {
    }

    /** Returns absolute wrapped facings with byte-exact {@code maxViolation <= feasTol}, or {@code null}. */
    public static double[] optimize(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        return optimize(exact, spec, feasTol, cancel, CLEARANCE, true, null, false, false, new Config(), null);
    }

    /** Like {@link #optimize}, but seeded from the given absolute wrapped facings instead of the dual
     *  recovery — typically a feasible point from another Solve-For direction, so phase 2 can ascend
     *  this spec's objective even where this direction's own dual recovery degenerates. */
    public static double[] optimize(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                    double[] seedAbsWrapped) {
        return optimize(exact, spec, feasTol, cancel, CLEARANCE, true, seedAbsWrapped, false, false, new Config(), null);
    }

    public static double[] optimize(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                    double[] seedAbsWrapped, Config cfg) {
        return optimize(exact, spec, feasTol, cancel, CLEARANCE, true, seedAbsWrapped, false, false, cfg, null);
    }

    public static double[] optimize(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                    double[] seedAbsWrapped, Config cfg, ClosestMiss miss) {
        return optimize(exact, spec, feasTol, cancel, CLEARANCE, true, seedAbsWrapped, false, false, cfg, miss);
    }

    public static double[] optimize(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                    double[] seedAbsWrapped, int phase1Calls, int totalCalls) {
        return optimize(exact, spec, feasTol, cancel, CLEARANCE, true, seedAbsWrapped, false, false,
                withCalls(phase1Calls, totalCalls), null);
    }

    public static double[] optimizeBestEffort(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                              double[] seedAbsWrapped, int phase1Calls, int totalCalls) {
        return optimize(exact, spec, feasTol, cancel, CLEARANCE, true, seedAbsWrapped, true, true,
                withCalls(phase1Calls, totalCalls), null);
    }

    public static double[] optimizeBestEffort(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                              double[] seedAbsWrapped, int phase1Calls, int totalCalls, boolean inertiaAware) {
        return optimize(exact, spec, feasTol, cancel, CLEARANCE, true, seedAbsWrapped, true, inertiaAware,
                withCalls(phase1Calls, totalCalls), null);
    }

    public static double[] optimizeBestEffort(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                              double[] seedAbsWrapped, int phase1Calls, int totalCalls, boolean inertiaAware,
                                              double trMinDeg) {
        Config cfg = withCalls(phase1Calls, totalCalls);
        cfg.trMinDeg = trMinDeg;
        return optimize(exact, spec, feasTol, cancel, CLEARANCE, true, seedAbsWrapped, true, inertiaAware, cfg, null);
    }

    /** Feasibility-only centered solve: phase 1 deepens clearance toward {@link Config#centerClearance}, the
     *  hugging phase 2 is skipped. For surrogate-objective solves (lead-in windows). */
    public static double[] optimizeCentered(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        Config cfg = new Config();
        return optimize(exact, spec, feasTol, cancel, cfg.centerClearance, false, null, false, false, cfg, null);
    }

    private static Config withCalls(int phase1Calls, int totalCalls) {
        Config cfg = new Config();
        cfg.phase1Calls = phase1Calls;
        cfg.totalCalls = totalCalls;
        return cfg;
    }

    private static double[] optimize(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                     double targetClearance, boolean hugObjective, double[] seedAbsWrapped,
                                     boolean bestEffort, boolean inertiaAware, Config cfg, ClosestMiss miss) {
        List<JumpConstraint> constraints = spec.constraints;
        JumpPhysicsInputs sc = spec.asScenario();
        YawTies ties = null;
        if (JumpLinearModel.hasFacingWall(constraints)) {
            ties = YawTies.of(constraints, sc.numTicks);
            if (ties == null) return null; // not position-linear
            for (JumpConstraint c : constraints) {
                if (c.mode != JumpConstraint.Mode.F) continue;
                if (c.t1 < 0 || c.t1 >= sc.numTicks) continue;
                boolean delta = c.t2 != null;
                boolean absorbed = delta
                        ? c.op == JumpConstraint.Op.MINUS && c.t2 == c.t1 - 1 && c.t1 >= 1
                                && ties.groupOf(c.t1) == ties.groupOf(c.t1 - 1)
                        : ties.varOf(c.t1) < 0;
                if (!absorbed) return null; // not position-linear
            }
        }
        for (JumpConstraint c : constraints) {
            if (c.mode == JumpConstraint.Mode.F) continue;
            if (c.cmp == JumpConstraint.Cmp.EQ) return null; // UI maps EQ to a corridor; a true EQ cannot hold strict clearance
        }

        JumpLinearModel lin = new JumpLinearModel(sc);
        int n = lin.n;

        boolean[] trivialInfeasible = {false};
        List<JumpConstraint> ineq = new ArrayList<>();
        List<JumpLinearModel.Wall> walls = new ArrayList<>();
        int domSign = (spec.objective.sense == Objective.Sense.MAX) ? 1 : -1;
        for (JumpConstraint c : constraints) {
            if (c.mode == JumpConstraint.Mode.F) continue;
            if (c.mode == JumpConstraint.Mode.DXZ || c.mode == JumpConstraint.Mode.DZX) {
                lin.addCrossAxisWalls(walls, c, domSign, 0.0);
                ineq.add(c);
                continue;
            }
            JumpLinearModel.Wall w = lin.compileWall(c, 0.0, trivialInfeasible);
            if (trivialInfeasible[0]) return null;
            if (w != null) { ineq.add(c); walls.add(w); }
        }
        int m = walls.size();
        if (m == 0) return null;

        double[] cx = new double[n];
        double[] cz = new double[n];
        lin.objectiveVectors(spec.objective, cx, cz); // MAX-normalized: c.u is always to maximize

        double[] theta;
        if (seedAbsWrapped != null) {
            theta = seedAbsWrapped.clone();
        } else {
            // Seed: the dual recovery at margin 0; infeasible here, but globally informed.
            CostateDualSolver dual = new CostateDualSolver(n, cx, cz, lin.mMagAll(), walls);
            CostateDualSolver.Result r = dual.solve(0.0, null);
            if (r == null) return null; // dual unbounded = continuous-infeasible certificate
            theta = new double[n];
            for (int t = 0; t < n; t++) {
                double gx = r.gx[t], gz = r.gz[t];
                if (gx * gx + gz * gz < 1.0e-18) {
                    boolean max = spec.objective.sense == Objective.Sense.MAX;
                    if (spec.objective.axis == JumpPhysicsInputs.Axis.X) { gx = max ? 1.0 : -1.0; gz = 0.0; }
                    else { gx = 0.0; gz = max ? 1.0 : -1.0; }
                }
                theta[t] = lin.recoverYawDeg(t, gx, gz);
            }
        }

        if (ties != null) theta = ties.expand(ties.reduce(theta));

        long t0 = System.nanoTime();
        if (SolverTrace.on()) {
            SolverTrace.log("SLP", "start n=%d m=%d seed=%s budget=%d/%d bestEffort=%s inertiaAware=%s fold=%s",
                    n, m, seedAbsWrapped != null ? "given" : "dual", cfg.phase1Calls, cfg.totalCalls, bestEffort,
                    inertiaAware, ties == null ? "-" : String.valueOf(ties.dims()));
        }
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double[] viol = new double[m];
        double[] candViol = new double[m];
        double[] ux = new double[n];
        double[] uz = new double[n];
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        int lpCalls = 0;
        double tr = cfg.trStartDeg;
        int dims = ties == null ? n : ties.dims();
        int[] col = new int[n];
        for (int t = 0; t < n; t++) col[t] = ties == null ? t : ties.varOf(t);

        boolean[] zeroX = new boolean[n];
        boolean[] zeroZ = new boolean[n];
        boolean[] lastZeroX = null;
        boolean[] lastZeroZ = null;
        List<JumpLinearModel.Wall> lpWalls = walls;
        double[] lpCx = cx;
        double[] lpCz = cz;

        for (int phase = 1; phase <= (hugObjective ? 2 : 1) && lpCalls < cfg.totalCalls; phase++) {
            int budget = phase == 1 ? cfg.phase1Calls : cfg.totalCalls;
            while (lpCalls < budget) {
                if (cancel != null && cancel.get()) {
                    offerMiss(miss, exact, sc, compiled, theta);
                    return null;
                }
                double[] gf = sc.toGameFacings(Angles.wrapAll(theta));
                ForwardPath path = exact.forward(sc, gf);
                double maxViol = exactSlacks(ineq, gf, path, viol);
                double objNorm = normObjective(path, spec.objective, max);
                if (phase == 1 && maxViol <= -targetClearance) break;

                if (inertiaAware) lin.zeroingPattern(theta, exact.inertiaThreshold(), exact.perAxisInertia(), zeroX, zeroZ);
                if (inertiaAware && (!patternEquals(lastZeroX, zeroX) || !patternEquals(lastZeroZ, zeroZ))) {
                    List<JumpLinearModel.Wall> rebuilt = null;
                    if (anySet(zeroX) || anySet(zeroZ)) {
                        JumpLinearModel patterned = new JumpLinearModel(sc, zeroX.clone(), zeroZ.clone());
                        rebuilt = new ArrayList<>(m);
                        for (JumpConstraint c : ineq) {
                            JumpLinearModel.Wall w = patterned.compileWall(c, 0.0, null);
                            if (w == null) { rebuilt = null; break; }
                            rebuilt.add(w);
                        }
                        if (rebuilt != null) {
                            lpWalls = rebuilt;
                            lpCx = new double[n];
                            lpCz = new double[n];
                            patterned.objectiveVectors(spec.objective, lpCx, lpCz);
                        }
                    }
                    if (rebuilt == null) {
                        lpWalls = walls;
                        lpCx = cx;
                        lpCz = cz;
                    }
                    lastZeroX = zeroX.clone();
                    lastZeroZ = zeroZ.clone();
                }

                for (int t = 0; t < n; t++) {
                    double phi = lin.baseArg(t) + theta[t] * RAD;
                    ux[t] = lin.mMag(t) * Math.cos(phi);
                    uz[t] = lin.mMag(t) * Math.sin(phi);
                }
                double[][] rows = new double[m][dims];
                for (int j = 0; j < m; j++) {
                    JumpLinearModel.Wall wall = lpWalls.get(j);
                    double[] row = rows[j];
                    double[] cxj = wall.coefX;
                    double[] czj = wall.coefZ;
                    for (int t = 0; t < n; t++) {
                        int v = col[t];
                        if (v < 0) continue;
                        double du = 0.0;
                        if (cxj != null && cxj[t] != 0.0) du += cxj[t] * -uz[t];
                        if (czj != null && czj[t] != 0.0) du += czj[t] * ux[t];
                        row[v] += du * RAD;
                    }
                }
                double[] objRow = null;
                if (phase != 1) {
                    objRow = new double[dims];
                    for (int t = 0; t < n; t++) {
                        int v = col[t];
                        if (v < 0) continue;
                        objRow[v] += -(lpCx[t] * -uz[t] + lpCz[t] * ux[t]) * RAD; // maximize c.du
                    }
                }
                lpCalls++;
                TrustRegionLp.Result lp = TrustRegionLp.solve(rows, viol, objRow, tr, phase == 1,
                        Math.max(-CLEARANCE, maxViol), cfg.lpMaxIter);
                if (lp == null) {
                    if (SolverTrace.on()) SolverTrace.log("SLP", "lp#%d phase=%d LP failed, phase stop", lpCalls, phase);
                    break; // LP infeasible/degenerate at this linearization: stop the phase
                }
                double[] d = lp.d;

                double[] cand = new double[n];
                double step = 0.0;
                for (int t = 0; t < n; t++) {
                    int v = col[t];
                    cand[t] = v < 0 ? theta[t] : theta[t] + d[v];
                }
                for (int v = 0; v < dims; v++) step = Math.max(step, Math.abs(d[v]));
                double[] cgf = sc.toGameFacings(Angles.wrapAll(cand));
                ForwardPath cpath = exact.forward(sc, cgf);
                double cViol = exactSlacks(ineq, cgf, cpath, candViol);
                double cObj = normObjective(cpath, spec.objective, max);
                // Phase 2 accepts any strictly feasible improvement; demanding extra clearance would
                // forbid hugging the very wall the objective optimizes into.
                boolean accept = phase == 1
                        ? cViol < maxViol
                        : cViol <= feasTol && cObj > objNorm;
                if (SolverTrace.on()) {
                    SolverTrace.log("SLP", "lp#%d phase=%d tr=%.3g step=%.3g pred=%.3e viol=%.3e->%.3e obj=%.9f->%.9f %s",
                            lpCalls, phase, tr, step, lp.s, maxViol, cViol, objNorm, cObj,
                            accept ? "accept" : "reject");
                }
                if (accept) {
                    theta = cand;
                    if (step > 0.8 * tr) tr = Math.min(tr * 2.0, cfg.trMaxDeg);
                } else {
                    tr *= 0.5;
                    if (tr < cfg.trMinDeg) break; // stalled on the float lattice: this phase is done
                }
            }
            if (phase == 1) {
                double[] gf = sc.toGameFacings(Angles.wrapAll(theta));
                double endViol = exactSlacks(ineq, gf, exact.forward(sc, gf), viol);
                // A hugging solve keeps any strictly feasible point (a tight corridor's best clearance can
                // be shallower than CLEARANCE); a centered solve keeps the demand, its result seeds windows.
                double phase1Gate = hugObjective ? feasTol : -CLEARANCE;
                if (endViol > phase1Gate) {
                    if (DEBUG) System.out.printf("  SLP infeasible: viol=%.3e after %d LPs (%.1f ms)%n",
                            endViol, lpCalls, (System.nanoTime() - t0) / 1e6);
                    if (SolverTrace.on()) {
                        SolverTrace.log("SLP", "phase1 infeasible viol=%.3e lps=%d ms=%.1f%s",
                                endViol, lpCalls, (System.nanoTime() - t0) / 1e6, bestEffort ? " (best effort kept)" : "");
                    }
                    if (!inertiaAware && !bestEffort) {
                        return optimize(exact, spec, feasTol, cancel, targetClearance, hugObjective,
                                Angles.wrapAll(theta), false, true, cfg, miss);
                    }
                    offerMiss(miss, exact, sc, compiled, theta);
                    return bestEffort ? Angles.wrapAll(theta) : null;
                }
                if (SolverTrace.on()) SolverTrace.log("SLP", "phase1 done viol=%.3e lps=%d", endViol, lpCalls);
                tr = 10.0; // phase 2 restarts from a workable step size (phase 1 may have collapsed it)
            }
        }

        double[] yaws = Angles.wrapAll(theta);
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath path = exact.forward(sc, gf);
        double finalViol = compiled.maxViolation(gf, path);
        if (DEBUG) System.out.printf("  SLP viol=%.3e obj=%.7f lps=%d (%.1f ms)%n", finalViol,
                path.getPos(spec.objective.tick, spec.objective.axis), lpCalls, (System.nanoTime() - t0) / 1e6);
        if (SolverTrace.on()) {
            SolverTrace.log("SLP", "end viol=%.3e obj=%.9f lps=%d ms=%.1f %s", finalViol,
                    path.getPos(spec.objective.tick, spec.objective.axis), lpCalls,
                    (System.nanoTime() - t0) / 1e6, finalViol <= feasTol ? "feasible" : (bestEffort ? "best effort" : "null"));
        }
        if (finalViol <= feasTol) return yaws;
        if (miss != null) miss.offer(yaws, finalViol);
        return bestEffort ? yaws : null;
    }

    private static void offerMiss(ClosestMiss miss, ExactJumpModel exact, JumpPhysicsInputs sc,
                                  JumpConstraintCompiler.Compiled compiled, double[] theta) {
        if (miss == null) return;
        double[] yaws = Angles.wrapAll(theta);
        double[] gf = sc.toGameFacings(yaws);
        miss.offer(yaws, compiled.maxViolation(gf, exact.forward(sc, gf)));
    }

    private static boolean patternEquals(boolean[] a, boolean[] b) {
        if (a == null) return !anySet(b);
        for (int t = 0; t < b.length; t++) if (a[t] != b[t]) return false;
        return true;
    }

    private static boolean anySet(boolean[] a) {
        for (boolean v : a) if (v) return true;
        return false;
    }

    /** Signed byte-exact slack per wall into {@code out} (&lt;= 0 = feasible, by that clearance); returns the max. */
    private static double exactSlacks(List<JumpConstraint> ineq, double[] gf, ForwardPath path, double[] out) {
        double mx = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < ineq.size(); j++) {
            JumpConstraint c = ineq.get(j);
            double e = JumpConstraintCompiler.evaluate(c, gf, path);
            double v = c.cmp == JumpConstraint.Cmp.GE ? -e : e;
            out[j] = v;
            if (v > mx) mx = v;
        }
        return mx;
    }

    /** Objective normalized so bigger is always better (MIN is negated), read off the byte-exact path. */
    private static double normObjective(ForwardPath path, Objective obj, boolean max) {
        double v = path.getPos(obj.tick, obj.axis);
        return max ? v : -v;
    }
}
