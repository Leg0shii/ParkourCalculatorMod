package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.profile.AttemptSampler;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DeepCheck {

    public static final long BUDGET_NANOS = 4_000_000_000L;
    private static final long MIN_SOLVE_NANOS = 60_000_000L;
    private static final int NODE_CAP = 1 << 20;

    private DeepCheck() {
    }

    public static double[] solve(ExactJumpModel exact, TurnProfileController.Current cur, TurnAttempt a,
                                 long budgetNanos, AtomicBoolean cancel) {
        TurnAttempt.Forecast f = a == null ? null : a.forecast;
        if (exact == null || cur == null || cur.snapshot == null || cur.landing == null || f == null || !f.hasState()) return null;
        JumpSpec full = cur.snapshot.spec;
        if (full.objective.isCustomAngle()) return null;
        int landingTick = cur.landing.tick - cur.startTick;
        JumpPhysicsInputs sc = full.asScenario();
        int n = Math.min(cur.n, Math.min(a.recorded, f.x.length));
        List<Integer> ticks = new ArrayList<Integer>();
        for (int t = 0; t < n; t++) {
            if (Double.isNaN(f.x[t]) || Double.isNaN(f.vx[t]) || Double.isNaN(a.yaws[t])) continue;
            boolean contact = !Double.isNaN(sc.slipAt(t));
            if (contact != f.ground[t]) continue;
            ticks.add(t);
        }
        if (ticks.isEmpty()) return null;
        double[] offsets = new double[cur.n];
        java.util.Arrays.fill(offsets, Double.NaN);
        long deadline = System.nanoTime() + budgetNanos;
        for (int i = ticks.size() - 1; i >= 0; i--) {
            if (cancel != null && cancel.get()) return null;
            long left = deadline - System.nanoTime();
            long per = Math.max(MIN_SOLVE_NANOS, left / (i + 1));
            int t = ticks.get(i);
            if (t >= landingTick) continue;
            offsets[t] = offsetAt(exact, full, sc, cur.facing, landingTick, cur.pixelDeg, a, t, per, cancel);
        }
        double floor = Double.NaN;
        for (int t = offsets.length - 1; t >= 0; t--) {
            if (Double.isNaN(offsets[t])) continue;
            if (!Double.isNaN(floor) && floor > offsets[t]) offsets[t] = floor;
            floor = offsets[t];
        }
        return offsets;
    }

    private static double offsetAt(ExactJumpModel exact, JumpSpec full, JumpPhysicsInputs sc, double[] facing,
                                   int landingTick, double pixelDeg, TurnAttempt a, int t, long nanos,
                                   AtomicBoolean cancel) {
        TurnAttempt.Forecast f = a.forecast;
        JumpPhysicsInputs slice = sc.slice(t, f.x[t], sc.startPos.y, f.z[t], f.vx[t], 0.0, f.vz[t], (float) a.yaws[t]);
        List<JumpConstraint> shifted = new ArrayList<JumpConstraint>();
        for (JumpConstraint c : full.constraints) {
            if (c.mode == JumpConstraint.Mode.F) continue;
            if (c.t1 < t || (c.t2 != null && c.t2 < t)) continue;
            shifted.add(new JumpConstraint(c.mode, c.t1 - t, c.t2 == null ? null : Integer.valueOf(c.t2 - t), c.op, c.cmp,
                    c.rhs, c.name, c.pin));
        }
        Objective o = full.objective;
        int objTick = Math.max(0, Math.min(slice.numTicks, landingTick - t));
        Objective objective = new Objective(o.axis, o.sense, objTick, 0.0, null, o.type);
        JumpConstraint goal = AngleSolverEngine.selectLegalGoalWall(shifted, objective, new String[1]);
        if (goal == null) return Double.NaN;
        JumpSpec withWall = new JumpSpec(slice, shifted, objective);
        List<JumpConstraint> others = new ArrayList<JumpConstraint>(shifted);
        others.remove(goal);
        long end = System.nanoTime() + nanos;
        int m = slice.numTicks;
        double[] seed = new double[m];
        double shift = f.bestOffset == null || t >= f.bestOffset.length || Double.isNaN(f.bestOffset[t]) ? 0.0 : f.bestOffset[t];
        seed[0] = Angles.wrap(facing[t]);
        for (int k = 1; k < m; k++) seed[k] = Angles.wrap(facing[t + k] + shift);
        boolean max = o.sense == Objective.Sense.MAX;
        Grid grid = new Grid(exact, slice, others, objective, goal, max, pixelDeg);
        double best = grid.search(seed, end);
        if (a.yaws.length >= t + m) {
            double[] real = new double[m];
            for (int k = 0; k < m; k++) real[k] = a.yaws[t + k];
            best = Math.max(best, grid.search(real, end));
        }
        if (best >= 0.0) return best;
        CertifiedBnb.Result r = run(exact, withWall, CertifiedBnb.Mode.FIRST_FEASIBLE, null, slice,
                Math.min(end, System.nanoTime() + nanos / 3), cancel);
        if (r.declined) return Double.NaN;
        if (r.feasible && r.yawsDeg != null) best = Math.max(best, grid.search(r.yawsDeg, end));
        if (best >= 0.0) return best;
        JumpSpec legal = new JumpSpec(slice, others, objective);
        r = run(exact, legal, CertifiedBnb.Mode.OPTIMIZE, seed, slice, end, cancel);
        if (r.declined) return best;
        if (r.feasible && r.yawsDeg != null) best = Math.max(best, grid.search(r.yawsDeg, end));
        return best;
    }

    private static final class Grid {
        private static final int[] STEPS = {1, -1, 2, -2, 4, -4, 8, -8, 16, -16};
        private final ExactJumpModel exact;
        private final JumpPhysicsInputs slice;
        private final JumpConstraintCompiler.Compiled comp;
        private final int objTick;
        private final boolean axisX;
        private final double rhs;
        private final boolean max;
        private final double pixelDeg;

        Grid(ExactJumpModel exact, JumpPhysicsInputs slice, List<JumpConstraint> others, Objective objective,
             JumpConstraint goal, boolean max, double pixelDeg) {
            this.exact = exact;
            this.slice = slice;
            this.comp = AttemptSampler.positionConstraints(new JumpSpec(slice, others, objective));
            this.objTick = objective.tick;
            this.axisX = objective.axis == JumpPhysicsInputs.Axis.X;
            this.rhs = goal.rhs;
            this.max = max;
            this.pixelDeg = pixelDeg;
        }

        double search(double[] yawsAbs, long deadline) {
            int m = slice.numTicks;
            if (yawsAbs == null || yawsAbs.length != m) return Double.NaN;
            double start = slice.startYaw;
            double[] cur = new double[m];
            for (int k = 0; k < m; k++) cur[k] = snap(yawsAbs[k], start);
            double best = eval(cur);
            boolean improved = true;
            while (improved && System.nanoTime() < deadline) {
                improved = false;
                for (int k = 0; k < m && System.nanoTime() < deadline; k++) {
                    for (int step : STEPS) {
                        double d = step * pixelDeg;
                        double[] one = cur.clone();
                        one[k] = Angles.wrap(one[k] + d);
                        double v = eval(one);
                        if (v > best) {
                            best = v;
                            cur = one;
                            improved = true;
                        }
                        double[] tail = cur.clone();
                        for (int j = k; j < m; j++) tail[j] = Angles.wrap(tail[j] + d);
                        v = eval(tail);
                        if (v > best) {
                            best = v;
                            cur = tail;
                            improved = true;
                        }
                    }
                }
            }
            return best;
        }

        private double snap(double yaw, double start) {
            return Angles.wrap(start + Math.round(Angles.wrapDelta(yaw - start) / pixelDeg) * pixelDeg);
        }

        private double eval(double[] abs) {
            double[] gf = slice.toGameFacings(abs.clone());
            ForwardPath p = exact.forward(slice, gf);
            double viol = comp.maxViolation(gf, p);
            double achieved = axisX ? p.posX[objTick] : p.posZ[objTick];
            double off = max ? achieved - rhs : rhs - achieved;
            return viol > 0.0 ? Math.min(off, -viol) : off;
        }
    }

    private static CertifiedBnb.Result run(ExactJumpModel exact, JumpSpec spec, CertifiedBnb.Mode mode, double[] seed,
                                           JumpPhysicsInputs slice, long deadline, AtomicBoolean cancel) {
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = mode;
        cfg.nodeCap = NODE_CAP;
        cfg.polishCap = mode == CertifiedBnb.Mode.OPTIMIZE ? 12 : 2;
        cfg.cancel = cancel;
        cfg.deadlineNanos = deadline;
        if (seed != null) {
            cfg.seedYaws = seed;
            cfg.seedPx = slice.startPos.x;
            cfg.seedPz = slice.startPos.z;
        }
        return CertifiedBnb.solve(exact, spec, cfg);
    }
}
