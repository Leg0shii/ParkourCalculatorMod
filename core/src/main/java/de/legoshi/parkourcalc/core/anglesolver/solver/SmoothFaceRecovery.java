package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

final class SmoothFaceRecovery {

    private static final double RAD = Math.PI / 180.0;
    private static final double FLOOR_DEG = 0.01;

    static final class Config {
        double restoreMargin = 5.0e-3;
        long deadlineNanos = 0L;
        boolean[] frozen = null;
        List<JumpConstraint> repairRows = null;
        boolean[] repairHeld = null;
        JumpSpec repairSpec = null;
        ExecutorService pool = null;
        int parallelism = 1;

        Config copy() {
            Config c = new Config();
            c.restoreMargin = restoreMargin;
            c.deadlineNanos = deadlineNanos;
            c.frozen = frozen;
            c.repairRows = repairRows;
            c.repairHeld = repairHeld;
            c.repairSpec = repairSpec;
            c.pool = pool;
            c.parallelism = parallelism;
            return c;
        }
    }

    private static final class Step {
        final double[] pdir;
        final double frac;

        Step(double[] pdir, double frac) {
            this.pdir = pdir;
            this.frac = frac;
        }
    }

    private static double[] tryStep(ExactJumpModel exact, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled compiled,
                                    Rows R, double[] y, double anchor, Step st, TurnShape cur, Config cfg) {
        int n = y.length;
        double[] c = y.clone();
        for (int t = 0; t < n; t++) c[t] += st.frac * st.pdir[t];
        holdFrozen(c, y, cfg.frozen);
        if (!restoreExact(R, exact, sc, compiled, c, cfg.restoreMargin, cfg.frozen, cfg)) return null;
        return TurnShape.of(anchor, c).betterThan(cur) ? c : null;
    }

    private static double[] firstBetter(ExactJumpModel exact, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled compiled,
                                        Rows R, double[] y, double anchor, List<Step> steps, TurnShape cur, Config cfg,
                                        AtomicBoolean cancel) {
        int par = cfg.pool == null ? 1 : Math.max(1, cfg.parallelism);
        for (int from = 0; from < steps.size(); from += par) {
            if (cancel.get() || expired(cfg)) return null;
            int to = Math.min(steps.size(), from + par);
            if (par == 1 || to - from == 1) {
                for (int i = from; i < to; i++) {
                    double[] c = tryStep(exact, sc, compiled, R, y, anchor, steps.get(i), cur, cfg);
                    if (c != null) return c;
                }
                continue;
            }
            List<Future<double[]>> futures = new ArrayList<Future<double[]>>();
            for (int i = from; i < to; i++) {
                final Step st = steps.get(i);
                futures.add(cfg.pool.submit(() -> tryStep(exact, sc, compiled, R, y, anchor, st, cur, cfg)));
            }
            double[] found = null;
            for (Future<double[]> f : futures) {
                try {
                    double[] c = f.get();
                    if (found == null && c != null) found = c;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            if (found != null) return found;
        }
        return null;
    }

    private static void holdFrozen(double[] cand, double[] base, boolean[] frozen) {
        if (frozen == null) return;
        for (int t = 0; t < frozen.length; t++) if (frozen[t]) cand[t] = base[t];
    }

    private static boolean expired(Config cfg) {
        return cfg.deadlineNanos != 0L && System.nanoTime() >= cfg.deadlineNanos;
    }

    private SmoothFaceRecovery() {
    }

    private static final class Row {
        final JumpLinearModel.Wall wall;
        final double nb;
        final boolean eq;
        final int fT1;
        final int fT2;
        final double fSign;
        final double fOp;
        final double fRhs;

        Row(JumpLinearModel.Wall wall) {
            this.wall = wall;
            this.nb = wall.bPrime;
            this.eq = wall.eq;
            this.fT1 = -1;
            this.fT2 = -1;
            this.fSign = 0.0;
            this.fOp = 0.0;
            this.fRhs = 0.0;
        }

        Row(int t1, int t2, double sign, double op, double rhs, boolean eq) {
            this.wall = null;
            this.nb = 0.0;
            this.eq = eq;
            this.fT1 = t1;
            this.fT2 = t2;
            this.fSign = sign;
            this.fOp = op;
            this.fRhs = rhs;
        }
    }

    private static final class Rows {
        JumpLinearModel lin;
        List<Row> rows;
        int n;
    }

    private static Rows build(JumpPhysicsInputs sc, JumpSpec spec, boolean[] zx, boolean[] zz) {
        Rows R = new Rows();
        R.n = sc.numTicks;
        R.lin = zx == null ? new JumpLinearModel(sc) : new JumpLinearModel(sc, zx, zz);
        R.rows = new ArrayList<Row>();
        for (JumpConstraint c : spec.constraints) {
            if (c.mode == JumpConstraint.Mode.X || c.mode == JumpConstraint.Mode.Z) {
                JumpLinearModel.Wall w = R.lin.compileWall(c, 0.0, null);
                if (w != null) R.rows.add(new Row(w));
            } else if (c.mode == JumpConstraint.Mode.F) {
                double op = c.op == JumpConstraint.Op.PLUS ? 1.0 : -1.0;
                int t2 = c.t2 == null ? -1 : c.t2;
                double sign = c.cmp == JumpConstraint.Cmp.GE ? -1.0 : 1.0;
                R.rows.add(new Row(c.t1, t2, sign, op, c.rhs, c.cmp == JumpConstraint.Cmp.EQ));
            }
        }
        return R;
    }

    private static Rows build(JumpPhysicsInputs sc, JumpSpec spec, boolean[][] pat) {
        return build(sc, spec, pat[0], pat[1]);
    }

    private static double rowValue(Rows R, Row r, double[] gf) {
        if (r.wall != null) return wallValue(R.lin, r.wall, gf);
        double raw = gf[r.fT1] + (r.fT2 >= 0 ? r.fOp * gf[r.fT2] : 0.0) - r.fRhs;
        return r.fSign * Angles.wrap(raw);
    }

    private static void rowGrad(Rows R, Row r, double[] gf, double[] out) {
        java.util.Arrays.fill(out, 0.0);
        if (r.wall != null) {
            wallGrad(R.lin, r.wall, gf, out);
            return;
        }
        out[r.fT1] += r.fSign;
        if (r.fT2 >= 0) out[r.fT2] += r.fSign * r.fOp;
    }

    private static double wallValue(JumpLinearModel lin, JumpLinearModel.Wall w, double[] gf) {
        double s = 0.0;
        for (int t = 0; t < lin.n; t++) {
            if (w.coef[t] == 0.0) continue;
            double phi = lin.baseArg(t) + gf[t] * RAD;
            double u = w.axis == 0 ? StrictMath.cos(phi) : StrictMath.sin(phi);
            s += w.coef[t] * lin.mMag(t) * u;
        }
        return s;
    }

    private static void wallGrad(JumpLinearModel lin, JumpLinearModel.Wall w, double[] gf, double[] out) {
        for (int t = 0; t < lin.n; t++) {
            if (w.coef[t] == 0.0) {
                out[t] = 0.0;
                continue;
            }
            double phi = lin.baseArg(t) + gf[t] * RAD;
            double d = w.axis == 0 ? -StrictMath.sin(phi) : StrictMath.cos(phi);
            out[t] = w.coef[t] * lin.mMag(t) * d * RAD;
        }
    }

    static double[] smoothToward(ExactJumpModel exact, JumpSpec spec, JumpConstraintCompiler.Compiled compiled,
                                 double[] seedYaws, double[] target, double feasTol, AtomicBoolean cancel, Config cfg) {
        if (seedYaws == null) return null;
        JumpPhysicsInputs sc = spec.asScenario();
        double anchor = sc.startYaw;
        double[] y = seedYaws.clone();
        if (exactViol(exact, sc, compiled, y) > feasTol) return null;
        if (target != null) {
            long tG = System.nanoTime();
            globalToward(exact, sc, spec, compiled, y, anchor, target, cfg, cancel);
            SmoothProfile.add("sfr.globalToward", tG);
        } else {
            long tF = System.nanoTime();
            faceWalk(exact, sc, spec, compiled, y, anchor, cfg, cancel);
            SmoothProfile.add("sfr.faceWalk", tF);
        }
        return exactViol(exact, sc, compiled, y) <= feasTol ? Angles.wrapAll(y) : null;
    }

    private static final int GLOBAL_ITERS = 32;

    private static void globalToward(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec,
                                     JumpConstraintCompiler.Compiled compiled, double[] y, double anchor,
                                     double[] target, Config cfg, AtomicBoolean cancel) {
        int n = sc.numTicks;
        for (int iter = 0; iter < GLOBAL_ITERS; iter++) {
            if (cancel.get() || expired(cfg)) break;
            TurnShape cur = TurnShape.of(anchor, y);
            Rows R = build(sc, spec, patternOf(sc, exact, y));
            double[] gfNow = sc.toGameFacings(y);
            double[] dir = new double[n];
            for (int t = 0; t < n; t++) dir[t] = Angles.wrapDelta(target[t] - y[t]);
            double[] pdir = projectInward(R, gfNow, cfg.restoreMargin, dir);
            double pn = 0.0;
            for (double v : pdir) pn = Math.max(pn, Math.abs(v));
            if (pn < 1.0e-12) break;
            List<Step> steps = new ArrayList<Step>();
            for (double frac = 1.0; frac >= 1.0 / 64.0; frac *= 0.5) steps.add(new Step(pdir, frac));
            double[] c = firstBetter(exact, sc, compiled, R, y, anchor, steps, cur, cfg, cancel);
            if (c == null) break;
            System.arraycopy(c, 0, y, 0, n);
        }
    }

    private static void faceWalk(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec,
                                 JumpConstraintCompiler.Compiled compiled, double[] y, double anchor,
                                 Config cfg, AtomicBoolean cancel) {
        int n = sc.numTicks;
        Set<String> stuck = new HashSet<String>();
        Map<String, Integer> touched = new HashMap<String, Integer>();
        int guard = 0;
        while (guard++ < 4 * n + 40) {
            if (cancel.get() || expired(cfg)) break;
            TurnShape cur = TurnShape.of(anchor, y);
            double[] dd = deltas(anchor, y);
            List<int[]> rs = runSpans(anchor, y);
            int[] pick = null;
            double pickMass = 0.0;
            for (int[] r : rs) {
                if (stuck.contains(r[0] + ":" + r[1])) continue;
                double mass = 0.0;
                for (int t = r[0]; t <= r[1]; t++) mass += dd[t];
                if (pick == null || Math.abs(mass) < Math.abs(pickMass)) {
                    pick = r;
                    pickMass = mass;
                }
            }
            if (pick == null) break;
            String key = pick[0] + ":" + pick[1];
            int nSeen = touched.getOrDefault(key, 0);
            if (nSeen >= 3) {
                stuck.add(key);
                continue;
            }
            touched.put(key, nSeen + 1);

            Rows R = build(sc, spec, patternOf(sc, exact, y));
            double[] gfNow = sc.toGameFacings(y);
            List<Step> steps = new ArrayList<Step>();
            for (int grow = 0; grow <= 16; grow++) {
                int lo = Math.max(0, pick[0] - grow);
                int hi = Math.min(n - 1, pick[1] + grow);
                double[] target = flattenYaw(anchor, y, lo, hi);
                double[] dir = new double[n];
                for (int t = 0; t < n; t++) dir[t] = Angles.wrapDelta(target[t] - y[t]);
                double[] pdir = projectInward(R, gfNow, cfg.restoreMargin, dir);
                double pn = 0.0;
                for (double v : pdir) pn = Math.max(pn, Math.abs(v));
                if (pn < 1.0e-12) continue;
                for (double frac = 1.0; frac >= 1.0 / 64.0; frac *= 0.5) steps.add(new Step(pdir, frac));
            }
            double[] c = firstBetter(exact, sc, compiled, R, y, anchor, steps, cur, cfg, cancel);
            if (c == null) {
                stuck.add(key);
                continue;
            }
            TurnShape next = TurnShape.of(anchor, c);
            System.arraycopy(c, 0, y, 0, n);
            if (next.reversals < cur.reversals) touched.clear();
        }
    }

    private static boolean[][] patternOf(JumpPhysicsInputs sc, ExactJumpModel exact, double[] yaws) {
        int n = sc.numTicks;
        boolean[] zx = new boolean[n];
        boolean[] zz = new boolean[n];
        new JumpLinearModel(sc).zeroingPattern(Angles.wrapAll(yaws), exact.inertiaThreshold(),
                exact.perAxisInertia(), zx, zz);
        return new boolean[][]{zx, zz};
    }

    private static boolean restoreExact(Rows R, ExactJumpModel exact, JumpPhysicsInputs sc,
                                        JumpConstraintCompiler.Compiled comp, double[] y, double margin,
                                        boolean[] frozen) {
        return restoreExact(R, exact, sc, comp, y, margin, frozen, null);
    }

    private static boolean restoreExact(Rows R, ExactJumpModel exact, JumpPhysicsInputs sc,
                                        JumpConstraintCompiler.Compiled comp, double[] y, double margin,
                                        boolean[] frozen, Config cfg) {
        if (exactViol(exact, sc, comp, y) <= 0.0) return true;
        if (cfg != null && cfg.repairRows != null) {
            double[] r = DeWiggle.repair(exact, sc, comp, cfg.repairSpec, cfg.repairRows, y, -1, -1, cfg.repairHeld, null);
            if (r != null && exactViol(exact, sc, comp, r) <= 0.0) {
                System.arraycopy(r, 0, y, 0, y.length);
                return true;
            }
        }
        return false;
    }

    private static double exactViol(ExactJumpModel exact, JumpPhysicsInputs sc,
                                    JumpConstraintCompiler.Compiled comp, double[] y) {
        double[] gf = sc.toGameFacings(y);
        return comp.maxViolation(gf, exact.forward(sc, gf));
    }

    private static double[] deltas(double anchor, double[] y) {
        double[] d = new double[y.length];
        double prev = anchor;
        for (int i = 0; i < y.length; i++) {
            d[i] = Angles.wrapDelta(y[i] - prev);
            prev = y[i];
        }
        return d;
    }

    private static List<int[]> runSpans(double anchor, double[] y) {
        List<int[]> out = new ArrayList<int[]>();
        double[] d = deltas(anchor, y);
        int last = 0;
        int start = 0;
        for (int t = 0; t < d.length; t++) {
            if (Math.abs(d[t]) <= FLOOR_DEG) continue;
            int sg = d[t] > 0 ? 1 : -1;
            if (last == 0) {
                last = sg;
                start = t;
                continue;
            }
            if (sg != last) {
                out.add(new int[]{start, t - 1});
                last = sg;
                start = t;
            }
        }
        if (last != 0) out.add(new int[]{start, d.length - 1});
        return out;
    }

    private static double[] flattenYaw(double anchor, double[] y, int lo, int hi) {
        int n = y.length;
        double left = lo == 0 ? anchor : y[lo - 1];
        double right = hi + 1 < n ? y[hi + 1] : y[hi];
        int steps = hi + 1 < n ? hi - lo + 2 : hi - lo + 1;
        double span = Angles.wrapDelta(right - left);
        double[] c = y.clone();
        for (int k = lo; k <= hi; k++) c[k] = left + span * (k - lo + 1.0) / steps;
        return c;
    }

    private static final int INWARD_SWEEPS = 6;

    private static double[] projectInward(Rows R, double[] gf, double margin, double[] dir) {
        int n = dir.length;
        List<Row> act = new ArrayList<Row>();
        for (Row r : R.rows) {
            double v = rowValue(R, r, gf) - r.nb;
            if (r.eq || v > -margin * 6.0) act.add(r);
        }
        double[] d = dir.clone();
        if (act.isEmpty()) return d;
        int m = act.size();
        double[][] J = new double[m][n];
        double[] gg = new double[m];
        double[] grad = new double[n];
        for (int i = 0; i < m; i++) {
            rowGrad(R, act.get(i), gf, grad);
            System.arraycopy(grad, 0, J[i], 0, n);
            double s = 0.0;
            for (int t = 0; t < n; t++) s += grad[t] * grad[t];
            gg[i] = s;
        }
        for (int sweep = 0; sweep < INWARD_SWEEPS; sweep++) {
            boolean changed = false;
            for (int i = 0; i < m; i++) {
                if (gg[i] < 1.0e-20) continue;
                double s = 0.0;
                for (int t = 0; t < n; t++) s += J[i][t] * d[t];
                if (!act.get(i).eq && s <= 0.0) continue;
                if (Math.abs(s) < 1.0e-15) continue;
                double f = s / gg[i];
                for (int t = 0; t < n; t++) d[t] -= f * J[i][t];
                changed = true;
            }
            if (!changed) break;
        }
        return d;
    }

}
