package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

final class SmoothFaceRecovery {

    private static final double RAD = Math.PI / 180.0;
    private static final double FLOOR_DEG = 0.01;
    private static final double W_EPS = 5.0e-3;
    private static final double EQ_TOL = 1.0e-9;

    static final class Config {
        double restoreMargin = 5.0e-3;
        long deadlineNanos = 0L;
        boolean[] frozen = null;
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
            double u = w.axis == 0 ? Math.cos(phi) : Math.sin(phi);
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
            double d = w.axis == 0 ? -Math.sin(phi) : Math.cos(phi);
            out[t] = w.coef[t] * lin.mMag(t) * d * RAD;
        }
    }

    private static double linWorst(Rows R, double[] gf) {
        double worst = Double.NEGATIVE_INFINITY;
        for (Row r : R.rows) {
            double v = rowValue(R, r, gf) - r.nb;
            worst = Math.max(worst, r.eq ? Math.abs(v) : v);
        }
        return worst;
    }

    static double[] smoothToward(ExactJumpModel exact, JumpSpec spec, JumpConstraintCompiler.Compiled compiled,
                                 double[] seedYaws, double[] target, double feasTol, AtomicBoolean cancel, Config cfg) {
        if (seedYaws == null) return null;
        JumpPhysicsInputs sc = spec.asScenario();
        double anchor = sc.startYaw;
        double[] y = seedYaws.clone();
        if (exactViol(exact, sc, compiled, y) > feasTol) return null;
        if (target != null) globalToward(exact, sc, spec, compiled, y, anchor, target, cfg, cancel);
        faceWalk(exact, sc, spec, compiled, y, anchor, cfg, cancel);
        return exactViol(exact, sc, compiled, y) <= feasTol ? Angles.wrapAll(y) : null;
    }

    private static final int GLOBAL_ITERS = 8;

    private static void globalToward(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec,
                                     JumpConstraintCompiler.Compiled compiled, double[] y, double anchor,
                                     double[] target, Config cfg, AtomicBoolean cancel) {
        int n = sc.numTicks;
        for (int iter = 0; iter < GLOBAL_ITERS; iter++) {
            if (cancel.get() || expired(cfg)) break;
            int curRev = reversals(anchor, y);
            double curJerk = jerkOf(anchor, y);
            Rows R = build(sc, spec, patternOf(sc, exact, y));
            double[][] P = tangentProjector(R, sc.toGameFacings(y), cfg.restoreMargin, n);
            double[] dir = new double[n];
            for (int t = 0; t < n; t++) dir[t] = Angles.wrapDelta(target[t] - y[t]);
            double[] pdir = apply(P, dir);
            double pn = 0.0;
            for (double v : pdir) pn = Math.max(pn, Math.abs(v));
            if (pn < 1.0e-12) break;
            boolean stepped = false;
            for (double frac = 1.0; frac >= 1.0 / 64.0 && !stepped; frac *= 0.5) {
                double[] c = y.clone();
                for (int t = 0; t < n; t++) c[t] += frac * pdir[t];
                holdFrozen(c, y, cfg.frozen);
                if (!restoreExact(R, exact, sc, compiled, c, cfg.restoreMargin, cfg.frozen)) continue;
                int rv = reversals(anchor, c);
                double jk = jerkOf(anchor, c);
                if (rv > curRev) continue;
                if (rv == curRev && jk >= curJerk - 1.0e-9) continue;
                System.arraycopy(c, 0, y, 0, n);
                stepped = true;
            }
            if (!stepped) break;
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
            int curRev = reversals(anchor, y);
            double curJerk = jerkOf(anchor, y);
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
            double[][] P = tangentProjector(R, sc.toGameFacings(y), cfg.restoreMargin, n);
            boolean done = false;
            for (int grow = 0; grow <= 16 && !done; grow++) {
                int lo = Math.max(0, pick[0] - grow);
                int hi = Math.min(n - 1, pick[1] + grow);
                double[] target = flattenYaw(anchor, y, lo, hi);
                double[] dir = new double[n];
                for (int t = 0; t < n; t++) dir[t] = Angles.wrapDelta(target[t] - y[t]);
                double[] pdir = apply(P, dir);
                double pn = 0.0;
                for (double v : pdir) pn = Math.max(pn, Math.abs(v));
                if (pn < 1.0e-12) continue;
                for (double frac = 1.0; frac >= 1.0 / 64.0 && !done; frac *= 0.5) {
                    double[] c = y.clone();
                    for (int t = 0; t < n; t++) c[t] += frac * pdir[t];
                    holdFrozen(c, y, cfg.frozen);
                    if (!restoreExact(R, exact, sc, compiled, c, cfg.restoreMargin, cfg.frozen)) continue;
                    int rv = reversals(anchor, c);
                    double jk = jerkOf(anchor, c);
                    if (rv > curRev) continue;
                    if (rv == curRev && jk >= curJerk - 1.0e-9) continue;
                    System.arraycopy(c, 0, y, 0, n);
                    done = true;
                    if (rv < curRev) touched.clear();
                }
            }
            if (!done) stuck.add(key);
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

    private static boolean restore(Rows R, double[] y, double margin, ExactJumpModel exact,
                                   JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled comp, boolean[] frozen) {
        int n = R.n;
        double[][] W = smoothMetric(n);
        double[] grad = new double[n];
        for (int it = 0; it < 200; it++) {
            double[] gf = sc.toGameFacings(y);
            List<Row> bad = new ArrayList<Row>();
            for (Row r : R.rows) {
                double v = rowValue(R, r, gf) - r.nb;
                if (r.eq ? Math.abs(v) > EQ_TOL : v > -margin) bad.add(r);
            }
            if (bad.isEmpty()) return true;
            int m = bad.size();
            double[][] J = new double[m][n];
            double[] res = new double[m];
            for (int i = 0; i < m; i++) {
                Row r = bad.get(i);
                rowGrad(R, r, gf, grad);
                System.arraycopy(grad, 0, J[i], 0, n);
                double target = r.eq ? r.nb : r.nb - margin;
                res[i] = target - rowValue(R, r, gf);
            }
            double[][] JW = new double[m][n];
            for (int a = 0; a < m; a++) {
                for (int x = 0; x < n; x++) {
                    double s = 0.0;
                    for (int z = 0; z < n; z++) s += J[a][z] * W[z][x];
                    JW[a][x] = s;
                }
            }
            double[][] jjt = new double[m][m];
            for (int a = 0; a < m; a++) {
                for (int b = 0; b < m; b++) {
                    double s = 0.0;
                    for (int x = 0; x < n; x++) s += JW[a][x] * J[b][x];
                    jjt[a][b] = s;
                }
            }
            double scale = 0.0;
            for (int a = 0; a < m; a++) scale = Math.max(scale, Math.abs(jjt[a][a]));
            double[] lam = solveSym(jjt, res, scale * 1.0e-10 + 1.0e-20);
            double best = linWorst(R, gf);
            boolean moved = false;
            for (double damp = 1.0; damp >= 1.0 / 512.0; damp *= 0.5) {
                double[] c = y.clone();
                for (int a = 0; a < m; a++) {
                    if (lam[a] == 0.0) continue;
                    for (int t = 0; t < n; t++) c[t] += damp * lam[a] * JW[a][t];
                }
                holdFrozen(c, y, frozen);
                double v = linWorst(R, sc.toGameFacings(c));
                if (v >= best - 1.0e-15) continue;
                System.arraycopy(c, 0, y, 0, n);
                moved = true;
                break;
            }
            if (!moved) return linWorst(R, sc.toGameFacings(y)) <= -margin * 0.5;
        }
        return linWorst(R, sc.toGameFacings(y)) <= 0.0;
    }

    private static boolean restoreExact(Rows R, ExactJumpModel exact, JumpPhysicsInputs sc,
                                        JumpConstraintCompiler.Compiled comp, double[] y, double margin,
                                        boolean[] frozen) {
        if (exactViol(exact, sc, comp, y) <= 0.0) return true;
        for (double m = margin; m <= margin * 16.0; m *= 2.0) {
            double[] c = y.clone();
            restore(R, c, m, exact, sc, comp, frozen);
            if (exactViol(exact, sc, comp, c) <= 0.0) {
                System.arraycopy(c, 0, y, 0, y.length);
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

    private static int reversals(double anchor, double[] y) {
        int c = 0;
        int last = 0;
        for (double v : deltas(anchor, y)) {
            if (Math.abs(v) <= FLOOR_DEG) continue;
            int s = v > 0 ? 1 : -1;
            if (last != 0 && s != last) c++;
            last = s;
        }
        return c;
    }

    private static double jerkOf(double anchor, double[] y) {
        double[] d = deltas(anchor, y);
        double s = 0.0;
        for (int i = 1; i < d.length; i++) s += Math.abs(d[i] - d[i - 1]);
        return s;
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

    private static double[][] metricCache;
    private static int metricN = -1;

    private static double[][] smoothMetric(int n) {
        if (metricN == n && metricCache != null) return metricCache;
        double[][] a = new double[n][n];
        double[] cf = {1.0, -2.0, 1.0};
        for (int t = 1; t < n - 1; t++) {
            int[] idx = {t - 1, t, t + 1};
            for (int i = 0; i < 3; i++) {
                for (int k = 0; k < 3; k++) a[idx[i]][idx[k]] += cf[i] * cf[k];
            }
        }
        for (int i = 0; i < n; i++) a[i][i] += W_EPS;
        metricCache = invert(a);
        metricN = n;
        return metricCache;
    }

    private static double[] solveSym(double[][] a, double[] b, double reg) {
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

    private static double[][] invert(double[][] a) {
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
            if (Math.abs(d) < 1.0e-16) d = d >= 0 ? 1.0e-16 : -1.0e-16;
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

    private static double[][] tangentProjector(Rows R, double[] gf, double margin, int n) {
        List<Row> act = new ArrayList<Row>();
        for (Row r : R.rows) {
            double v = rowValue(R, r, gf) - r.nb;
            if (r.eq || v > -margin * 6.0) act.add(r);
        }
        double[][] P = new double[n][n];
        for (int i = 0; i < n; i++) P[i][i] = 1.0;
        if (act.isEmpty()) return P;
        int m = act.size();
        double[][] J = new double[m][n];
        double[] grad = new double[n];
        for (int i = 0; i < m; i++) {
            rowGrad(R, act.get(i), gf, grad);
            System.arraycopy(grad, 0, J[i], 0, n);
        }
        double[][] jjt = new double[m][m];
        for (int a = 0; a < m; a++) {
            for (int b = 0; b < m; b++) {
                double s = 0.0;
                for (int t = 0; t < n; t++) s += J[a][t] * J[b][t];
                jjt[a][b] = s;
            }
        }
        double scale = 0.0;
        for (int a = 0; a < m; a++) scale = Math.max(scale, Math.abs(jjt[a][a]));
        double[][] inv = invert(addReg(jjt, scale * 1.0e-10 + 1.0e-20));
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                double s = 0.0;
                for (int a = 0; a < m; a++) {
                    for (int b = 0; b < m; b++) s += J[a][r] * inv[a][b] * J[b][c];
                }
                P[r][c] -= s;
            }
        }
        return P;
    }

    private static double[][] addReg(double[][] a, double reg) {
        double[][] o = new double[a.length][a.length];
        for (int i = 0; i < a.length; i++) {
            System.arraycopy(a[i], 0, o[i], 0, a.length);
            o[i][i] += reg;
        }
        return o;
    }

    private static double[] apply(double[][] P, double[] v) {
        double[] o = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            double s = 0.0;
            for (int k = 0; k < v.length; k++) s += P[i][k] * v[k];
            o[i] = s;
        }
        return o;
    }
}
