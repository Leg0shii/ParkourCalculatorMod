package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.List;

final class BandQp {

    static final int BAND = 3;

    private static final int MAX_SWEEPS = 600;
    private static final double SWEEP_TOL = 1.0e-10;
    private static final int MAX_ROUNDS = 12;
    private static final double ROW_TOL = 1.0e-9;

    private BandQp() {
    }

    static final class Factor {
        private final double[][] l;
        private final int n;

        private Factor(double[][] l, int n) {
            this.l = l;
            this.n = n;
        }

        double[] solve(double[] b) {
            double[] y = new double[n];
            for (int i = 0; i < n; i++) {
                double s = b[i];
                double[] li = l[i];
                for (int k = 1; k <= BAND && k <= i; k++) {
                    s -= li[k] * y[i - k];
                }
                y[i] = s / li[0];
            }
            for (int i = n - 1; i >= 0; i--) {
                double s = y[i];
                for (int k = 1; k <= BAND && i + k < n; k++) {
                    s -= l[i + k][k] * y[i + k];
                }
                y[i] = s / l[i][0];
            }
            return y;
        }
    }

    static Factor factor(double[][] band, double damping) {
        int n = band.length;
        double scale = 0.0;
        for (int i = 0; i < n; i++) {
            scale = Math.max(scale, band[i][0]);
        }
        double reg = damping * Math.max(scale, 1.0e-6) + 1.0e-9;
        double[][] l = new double[n][BAND + 1];
        for (int i = 0; i < n; i++) {
            for (int k = Math.min(BAND, i); k >= 0; k--) {
                int j = i - k;
                double s = band[i][k] + (k == 0 ? reg : 0.0);
                for (int p = 1; p <= BAND - k && p <= j; p++) {
                    s -= l[i][k + p] * l[j][p];
                }
                if (k == 0) {
                    l[i][0] = StrictMath.sqrt(Math.max(s, 1.0e-300));
                } else {
                    l[i][k] = s / l[j][0];
                }
            }
        }
        return new Factor(l, n);
    }

    static double[] solve(Factor factor, double[] gradient, List<double[]> rows, List<Double> rhs, double multiplierCap) {
        int n = gradient.length;
        double[] hg = factor.solve(gradient);
        double[] step = new double[n];
        for (int i = 0; i < n; i++) {
            step[i] = -hg[i];
        }
        int m = rows.size();
        if (m == 0) {
            return step;
        }
        double[][] ha = new double[m][];
        double[] lam = new double[m];
        boolean[] active = new boolean[m];
        int[] work = new int[m];
        int count = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            int added = 0;
            for (int i = 0; i < m; i++) {
                if (active[i]) {
                    continue;
                }
                if (dot(rows.get(i), step, n) < rhs.get(i) - ROW_TOL) {
                    active[i] = true;
                    ha[i] = factor.solve(rows.get(i));
                    work[count++] = i;
                    added++;
                }
            }
            if (added == 0) {
                break;
            }
            dualAscent(rows, rhs, ha, hg, lam, work, count, n, multiplierCap);
            for (int x = 0; x < n; x++) {
                step[x] = -hg[x];
            }
            for (int w = 0; w < count; w++) {
                double l = lam[work[w]];
                if (l == 0.0) {
                    continue;
                }
                double[] hi = ha[work[w]];
                for (int x = 0; x < n; x++) {
                    step[x] += l * hi[x];
                }
            }
        }
        return step;
    }

    private static void dualAscent(List<double[]> rows, List<Double> rhs, double[][] ha, double[] hg, double[] lam,
                                   int[] work, int count, int n, double cap) {
        double[][] q = new double[count][count];
        double[] lin = new double[count];
        double[] cur = new double[count];
        for (int i = 0; i < count; i++) {
            double[] ai = rows.get(work[i]);
            lin[i] = dot(ai, hg, n) + rhs.get(work[i]);
            cur[i] = lam[work[i]];
            for (int k = i; k < count; k++) {
                double v = dot(ai, ha[work[k]], n);
                q[i][k] = v;
                q[k][i] = v;
            }
        }
        for (int sweep = 0; sweep < MAX_SWEEPS; sweep++) {
            double change = 0.0;
            for (int i = 0; i < count; i++) {
                double qi = q[i][i];
                if (qi <= 1.0e-18) {
                    continue;
                }
                double v = lin[i];
                double[] qr = q[i];
                for (int k = 0; k < count; k++) {
                    v -= qr[k] * cur[k];
                }
                double next = Math.min(cap, Math.max(0.0, cur[i] + v / qi));
                change = Math.max(change, Math.abs(next - cur[i]) * StrictMath.sqrt(qi));
                cur[i] = next;
            }
            if (change < SWEEP_TOL) {
                break;
            }
        }
        for (int i = 0; i < count; i++) {
            lam[work[i]] = cur[i];
        }
    }

    private static double dot(double[] a, double[] b, int n) {
        double v = 0.0;
        for (int x = 0; x < n; x++) {
            v += a[x] * b[x];
        }
        return v;
    }
}
