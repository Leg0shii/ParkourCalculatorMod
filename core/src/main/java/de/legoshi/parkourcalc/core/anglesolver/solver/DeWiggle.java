package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Removes the short turn segments a feasibility repair leaves behind. The solved facing path splits into
 *  maximal same-sign turn runs; a human's runs all carry tens of degrees, while a repaired path carries
 *  runs of a fraction of a degree, which read as flicks. This pass takes each run below {@link #MIN_ARC_DEG},
 *  replaces its span with a constant turn rate, and restores byte-exact feasibility with a Gauss-Newton step
 *  that holds the flattened span fixed and measures correction size in second differences, so the restore
 *  cannot re-introduce a flick. A candidate is kept only when it is strictly feasible and carries fewer
 *  runs than before, so the pass can never make a path worse. */
public final class DeWiggle {

    public static final double MIN_ARC_DEG = 45.0;
    private static final double FLOOR_DEG = 0.01;
    private static final int MAX_GROW = 12;
    private static final double METRIC_EPS = 5.0e-3;
    private static final double MARGIN = 2.0e-4;
    private static final int NEWTON_ITERS = 40;
    private static final double FEAS_TOL = 0.0;

    private DeWiggle() {
    }

    public static double[] run(ForwardModel model, JumpSpec spec, double[] yawsAbsWrapped, AtomicBoolean cancel) {
        if (yawsAbsWrapped == null || yawsAbsWrapped.length < 3) return yawsAbsWrapped;
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        List<JumpConstraint> cs = usable(sc, spec);
        if (cs.isEmpty()) return yawsAbsWrapped;
        double anchor = sc.startYaw;
        double[] y = Angles.wrapAll(yawsAbsWrapped.clone());
        if (violation(model, sc, compiled, y) > FEAS_TOL) return yawsAbsWrapped;

        Set<String> stuck = new HashSet<>();
        while (true) {
            if (cancel != null && cancel.get()) return y;
            List<int[]> rs = runs(anchor, y);
            int arcs = rs.size();
            int[] pick = null;
            double pickMass = 0.0;
            for (int[] r : rs) {
                double mass = Math.abs(mass(anchor, y, r[0], r[1]));
                if (mass >= MIN_ARC_DEG) continue;
                if (stuck.contains(r[0] + ":" + r[1])) continue;
                if (pick == null || mass < pickMass) {
                    pick = r;
                    pickMass = mass;
                }
            }
            if (pick == null) return y;
            double[] taken = null;
            for (int g = 0; g <= MAX_GROW && taken == null; g++) {
                for (int side = 0; side < 3 && taken == null; side++) {
                    int lo = pick[0];
                    int hi = pick[1];
                    if (side == 0 || side == 1) lo = Math.max(0, pick[0] - g);
                    if (side == 0 || side == 2) hi = Math.min(y.length - 1, pick[1] + g);
                    if (g > 0 && side > 0 && lo == pick[0] && hi == pick[1]) continue;
                    double[] c = flatten(anchor, y, lo, hi);
                    if (runs(anchor, c).size() >= arcs) continue;
                    double viol = violation(model, sc, compiled, c);
                    if (viol > FEAS_TOL) {
                        c = repair(model, sc, compiled, spec, cs, c, lo, hi, cancel);
                        if (c == null) continue;
                        if (runs(anchor, c).size() >= arcs) continue;
                    }
                    taken = c;
                }
            }
            if (taken == null) stuck.add(pick[0] + ":" + pick[1]);
            else y = taken;
        }
    }

    private static double[] flatten(double anchor, double[] y, int lo, int hi) {
        int n = y.length;
        double left = lo == 0 ? anchor : y[lo - 1];
        double right = hi + 1 < n ? y[hi + 1] : y[hi];
        int steps = hi + 1 < n ? hi - lo + 2 : hi - lo + 1;
        double span = Angles.wrapDelta(right - left);
        double[] c = y.clone();
        for (int k = lo; k <= hi; k++) c[k] = Angles.wrap(left + span * (k - lo + 1.0) / steps);
        return c;
    }

    private static double[] repair(ForwardModel model, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled compiled,
                                   JumpSpec spec, List<JumpConstraint> cs, double[] start, int lo, int hi,
                                   AtomicBoolean cancel) {
        int n = start.length;
        double[] y = start.clone();
        double best = violation(model, sc, compiled, y);
        int nf = 0;
        for (int t = 0; t < n; t++) if (t < lo || t > hi) nf++;
        if (nf == 0) return null;
        int[] free = new int[nf];
        int fi = 0;
        for (int t = 0; t < n; t++) if (t < lo || t > hi) free[fi++] = t;
        double[][] wmat = smoothMetric(free, n);

        for (int it = 0; it < NEWTON_ITERS && best > FEAS_TOL; it++) {
            if (cancel != null && cancel.get()) return null;
            double[] r = residuals(model, sc, spec, cs, y);
            boolean[] keep = new boolean[r.length];
            int m = 0;
            for (int i = 0; i < r.length; i++) {
                keep[i] = r[i] < MARGIN * 4.0;
                if (keep[i]) m++;
            }
            if (m == 0) break;
            double[][] j = jacobian(sc, y, cs, keep);
            double[] want = new double[m];
            int q = 0;
            for (int i = 0; i < r.length; i++) {
                if (keep[i]) want[q++] = Math.max(0.0, MARGIN - r[i]);
            }
            double[][] jw = new double[m][nf];
            for (int a = 0; a < m; a++) {
                for (int x = 0; x < nf; x++) {
                    double s = 0.0;
                    for (int z = 0; z < nf; z++) s += j[a][free[z]] * wmat[z][x];
                    jw[a][x] = s;
                }
            }
            double[][] jjt = new double[m][m];
            for (int a = 0; a < m; a++) {
                for (int b = 0; b < m; b++) {
                    double s = 0.0;
                    for (int x = 0; x < nf; x++) s += jw[a][x] * j[b][free[x]];
                    jjt[a][b] = s;
                }
            }
            double scale = 0.0;
            for (int a = 0; a < m; a++) scale = Math.max(scale, Math.abs(jjt[a][a]));
            double[] lam = solveSym(jjt, want, scale * 1.0e-8 + 1.0e-18);
            double[] dy = new double[n];
            for (int a = 0; a < m; a++) {
                if (lam[a] == 0.0) continue;
                for (int x = 0; x < nf; x++) dy[free[x]] += lam[a] * jw[a][x];
            }
            boolean stepped = false;
            for (double damp = 1.0; damp >= 1.0 / 64.0; damp *= 0.5) {
                double[] c = new double[n];
                for (int t = 0; t < n; t++) c[t] = Angles.wrap(y[t] + damp * dy[t]);
                double v = violation(model, sc, compiled, c);
                if (v < best - 1.0e-15 || v <= FEAS_TOL) {
                    y = c;
                    best = v;
                    stepped = true;
                    break;
                }
            }
            if (!stepped) break;
        }
        return best <= FEAS_TOL ? y : null;
    }

    private static List<JumpConstraint> usable(JumpPhysicsInputs sc, JumpSpec spec) {
        JumpLinearModel lin = new JumpLinearModel(sc);
        List<JumpConstraint> out = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) {
            if (c.t2 != null) continue;
            if (c.mode != JumpConstraint.Mode.X && c.mode != JumpConstraint.Mode.Z) continue;
            if (lin.compileWall(c, 0.0, null) == null) continue;
            out.add(c);
        }
        return out;
    }

    private static double[] residuals(ForwardModel model, JumpPhysicsInputs sc, JumpSpec spec,
                                      List<JumpConstraint> cs, double[] y) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(y));
        ForwardPath p = model.forward(sc, gf);
        double[] a = new double[cs.size()];
        for (int i = 0; i < cs.size(); i++) {
            JumpConstraint c = cs.get(i);
            JumpPhysicsInputs.Axis ax = c.mode == JumpConstraint.Mode.X
                    ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z;
            double got = p.getPos(c.t1, ax);
            if (c.cmp == JumpConstraint.Cmp.GE) a[i] = got - c.rhs;
            else if (c.cmp == JumpConstraint.Cmp.LE) a[i] = c.rhs - got;
            else a[i] = -Math.abs(got - c.rhs);
        }
        return a;
    }

    private static double[][] jacobian(JumpPhysicsInputs sc, double[] y, List<JumpConstraint> cs, boolean[] keep) {
        JumpLinearModel lin = new JumpLinearModel(sc);
        int n = y.length;
        double[] gf = sc.toGameFacings(Angles.wrapAll(y));
        double rad = Math.PI / 180.0;
        double[] ux = new double[n];
        double[] uz = new double[n];
        for (int t = 0; t < n; t++) {
            double phi = lin.baseArg(t) + gf[t] * rad;
            ux[t] = lin.mMag(t) * Math.cos(phi);
            uz[t] = lin.mMag(t) * Math.sin(phi);
        }
        List<double[]> rows = new ArrayList<>();
        for (int i = 0; i < cs.size(); i++) {
            if (!keep[i]) continue;
            JumpLinearModel.Wall w = lin.compileWall(cs.get(i), 0.0, null);
            double[] row = new double[n];
            for (int t = 0; t < n; t++) {
                double du = w.axis == 0 ? -uz[t] : ux[t];
                row[t] = -w.coef[t] * du * rad;
            }
            rows.add(row);
        }
        return rows.toArray(new double[0][]);
    }

    private static double[][] smoothMetric(int[] free, int n) {
        int f = free.length;
        double[][] a = new double[f][f];
        int[] pos = new int[n];
        for (int i = 0; i < n; i++) pos[i] = -1;
        for (int i = 0; i < f; i++) pos[free[i]] = i;
        double[] cf = {1.0, -2.0, 1.0};
        for (int t = 1; t < n - 1; t++) {
            int[] idx = {t - 1, t, t + 1};
            for (int i = 0; i < 3; i++) {
                int pi = pos[idx[i]];
                if (pi < 0) continue;
                for (int k = 0; k < 3; k++) {
                    int pk = pos[idx[k]];
                    if (pk < 0) continue;
                    a[pi][pk] += cf[i] * cf[k];
                }
            }
        }
        for (int i = 0; i < f; i++) a[i][i] += METRIC_EPS;
        return invert(a);
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
            if (Math.abs(d) < 1.0e-14) d = d >= 0.0 ? 1.0e-14 : -1.0e-14;
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
            if (Math.abs(w[c][c]) < 1.0e-14) continue;
            for (int r = 0; r < m; r++) {
                if (r == c) continue;
                double f = w[r][c] / w[c][c];
                if (f == 0.0) continue;
                for (int k = c; k <= m; k++) w[r][k] -= f * w[c][k];
            }
        }
        double[] x = new double[m];
        for (int i = 0; i < m; i++) x[i] = Math.abs(w[i][i]) < 1.0e-14 ? 0.0 : w[i][m] / w[i][i];
        return x;
    }

    private static double violation(ForwardModel model, JumpPhysicsInputs sc,
                                    JumpConstraintCompiler.Compiled compiled, double[] y) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(y));
        return compiled.maxViolation(gf, model.forward(sc, gf));
    }

    private static double delta(double anchor, double[] y, int t) {
        return Angles.wrapDelta(y[t] - (t == 0 ? anchor : y[t - 1]));
    }

    private static double mass(double anchor, double[] y, int lo, int hi) {
        double s = 0.0;
        for (int t = lo; t <= hi; t++) s += delta(anchor, y, t);
        return s;
    }

    public static List<int[]> runs(double anchor, double[] y) {
        List<int[]> out = new ArrayList<>();
        int last = 0;
        int start = 0;
        for (int t = 0; t < y.length; t++) {
            double d = delta(anchor, y, t);
            if (Math.abs(d) <= FLOOR_DEG) continue;
            int s = d > 0.0 ? 1 : -1;
            if (last == 0) {
                last = s;
                start = t;
                continue;
            }
            if (s != last) {
                out.add(new int[] {start, t - 1});
                last = s;
                start = t;
            }
        }
        if (last != 0) out.add(new int[] {start, y.length - 1});
        return out;
    }
}
