package de.legoshi.parkourcalc.core.anglesolver.solver;

final class TrustRegionLp {

    private static final double EPS_RC = 1.0e-9;
    private static final double EPS_PIV = 1.0e-10;
    private static final double EPS_FEAS = 1.0e-9;
    private static final int STALL_LIMIT = 400;
    private static final int REFACTOR_EVERY = 100;

    static final class Result {
        final double[] d;
        final double s;

        Result(double[] d, double s) {
            this.d = d;
            this.s = s;
        }
    }

    private final int n;
    private final int m;
    private final double[][] a;
    private final double[] rhs;
    private final double tr;
    private final int sVar;
    private final int total;
    private final double[] cost;
    private final int[] basis;
    private final byte[] state;
    private final double[][] binv;
    private final double[] xB;
    private final double[] y;
    private final double[] w;
    private double sHi = Double.POSITIVE_INFINITY;
    private boolean bland;
    private int stall;
    private double lastZ = Double.POSITIVE_INFINITY;
    private int pivotCount;

    private TrustRegionLp(double[][] a, double[] viol, double tr) {
        this.a = a;
        this.m = a.length;
        this.n = m > 0 ? a[0].length : 0;
        this.tr = tr;
        this.sVar = 2 * n;
        this.total = 2 * n + 1 + m;
        this.rhs = new double[m];
        for (int j = 0; j < m; j++) rhs[j] = -viol[j];
        this.cost = new double[total];
        this.basis = new int[m];
        this.state = new byte[total];
        this.binv = new double[m][m];
        this.xB = new double[m];
        this.y = new double[m];
        this.w = new double[m];
    }

    static Result solve(double[][] a, double[] viol, double[] obj, double tr, boolean phase1, double sCap, int maxIter) {
        TrustRegionLp lp = new TrustRegionLp(a, viol, tr);
        return lp.run(obj, phase1, sCap, maxIter);
    }

    private Result run(double[] obj, boolean phase1, double sCap, int maxIter) {
        int jStar = 0;
        for (int j = 1; j < m; j++) {
            if (-rhs[j] > -rhs[jStar]) jStar = j;
        }
        for (int v = 0; v < total; v++) state[v] = 0;
        for (int j = 0; j < m; j++) {
            basis[j] = j == jStar ? sVar : 2 * n + 1 + j;
            state[basis[j]] = 2;
        }
        refactor();
        cost[sVar] = 1.0;
        int iters = iterate(maxIter, phase1 ? Double.NEGATIVE_INFINITY : sCap);
        if (iters < 0) return null;
        double sVal = currentS();
        if (phase1) {
            return new Result(extractD(), sVal);
        }
        if (sVal > sCap + EPS_FEAS) return null;
        sHi = sCap;
        cost[sVar] = 0.0;
        if (obj != null) {
            for (int v = 0; v < n; v++) {
                cost[v] = obj[v];
                cost[n + v] = -obj[v];
            }
        }
        bland = false;
        stall = 0;
        lastZ = Double.POSITIVE_INFINITY;
        int it2 = iterate(maxIter - iters, Double.NEGATIVE_INFINITY);
        if (it2 < 0) return null;
        return new Result(extractD(), sCap);
    }

    private int iterate(int maxIter, double sTarget) {
        int used = 0;
        while (used < maxIter) {
            if (sTarget > Double.NEGATIVE_INFINITY && currentS() <= sTarget) return used;
            computeDuals();
            int q = pickEntering();
            if (q < 0) return used;
            used++;
            if (!pivotOrFlip(q)) return -1;
            trackStall();
        }
        return sTarget > Double.NEGATIVE_INFINITY && currentS() <= sTarget ? used : -1;
    }

    private double currentS() {
        for (int r = 0; r < m; r++) {
            if (basis[r] == sVar) return xB[r];
        }
        return state[sVar] == 1 ? sHi : Double.NEGATIVE_INFINITY;
    }

    private void computeDuals() {
        for (int k = 0; k < m; k++) {
            double s = 0.0;
            for (int r = 0; r < m; r++) {
                double cb = cost[basis[r]];
                if (cb != 0.0) s += cb * binv[r][k];
            }
            y[k] = s;
        }
    }

    private double reducedCost(int v) {
        if (v < n) {
            double s = cost[v];
            for (int r = 0; r < m; r++) s -= y[r] * a[r][v];
            return s;
        }
        if (v < 2 * n) {
            double s = cost[v];
            int u = v - n;
            for (int r = 0; r < m; r++) s += y[r] * a[r][u];
            return s;
        }
        if (v == sVar) {
            double s = cost[sVar];
            for (int r = 0; r < m; r++) s += y[r];
            return s;
        }
        return -y[v - 2 * n - 1];
    }

    private int pickEntering() {
        int best = -1;
        double bestMag = EPS_RC;
        for (int v = 0; v < total; v++) {
            byte st = state[v];
            if (st == 2) continue;
            double rc = reducedCost(v);
            double mag = st == 0 ? -rc : rc;
            if (mag > bestMag) {
                if (bland) return v;
                bestMag = mag;
                best = v;
            }
        }
        return best;
    }

    private void column(int v, double[] out) {
        if (v < n) {
            for (int r = 0; r < m; r++) out[r] = a[r][v];
        } else if (v < 2 * n) {
            int u = v - n;
            for (int r = 0; r < m; r++) out[r] = -a[r][u];
        } else if (v == sVar) {
            for (int r = 0; r < m; r++) out[r] = -1.0;
        } else {
            java.util.Arrays.fill(out, 0.0);
            out[v - 2 * n - 1] = 1.0;
        }
    }

    private double loOf(int v) {
        if (v == sVar) return Double.NEGATIVE_INFINITY;
        return 0.0;
    }

    private double hiOf(int v) {
        if (v < 2 * n) return tr;
        if (v == sVar) return sHi;
        return Double.POSITIVE_INFINITY;
    }

    private boolean pivotOrFlip(int q) {
        double[] aq = new double[m];
        column(q, aq);
        for (int r = 0; r < m; r++) {
            double s = 0.0;
            for (int k = 0; k < m; k++) s += binv[r][k] * aq[k];
            w[r] = s;
        }
        double sigma = state[q] == 0 ? 1.0 : -1.0;
        double t = hiOf(q) - loOf(q);
        int leave = -1;
        for (int r = 0; r < m; r++) {
            double rate = -sigma * w[r];
            int b = basis[r];
            if (rate < -EPS_PIV) {
                double lo = loOf(b);
                if (lo == Double.NEGATIVE_INFINITY) continue;
                double tc = (xB[r] - lo) / (-rate);
                if (tc < t - 1.0e-15 || (tc < t + 1.0e-15 && (leave < 0 || b < basis[leave]))) {
                    t = tc;
                    leave = r;
                }
            } else if (rate > EPS_PIV) {
                double hi = hiOf(b);
                if (hi == Double.POSITIVE_INFINITY) continue;
                double tc = (hi - xB[r]) / rate;
                if (tc < t - 1.0e-15 || (tc < t + 1.0e-15 && (leave < 0 || b < basis[leave]))) {
                    t = tc;
                    leave = r;
                }
            }
        }
        if (t == Double.POSITIVE_INFINITY) return false;
        if (t < 0.0) t = 0.0;
        if (leave < 0) {
            for (int r = 0; r < m; r++) xB[r] -= sigma * t * w[r];
            state[q] = state[q] == 0 ? (byte) 1 : (byte) 0;
            return true;
        }
        int b = basis[leave];
        double rate = -sigma * w[leave];
        state[b] = rate < 0.0 ? (byte) 0 : (byte) 1;
        double enterVal = state[q] == 0 ? loOf(q) + t : hiOf(q) - t;
        for (int r = 0; r < m; r++) {
            if (r != leave) xB[r] -= sigma * t * w[r];
        }
        xB[leave] = enterVal;
        basis[leave] = q;
        state[q] = 2;
        double piv = w[leave];
        if (Math.abs(piv) < EPS_PIV) return false;
        double[] br = binv[leave];
        for (int k = 0; k < m; k++) br[k] /= piv;
        for (int r = 0; r < m; r++) {
            if (r == leave) continue;
            double f = w[r];
            if (f == 0.0) continue;
            double[] rr = binv[r];
            for (int k = 0; k < m; k++) rr[k] -= f * br[k];
        }
        if (++pivotCount % REFACTOR_EVERY == 0) refactor();
        return true;
    }

    private void trackStall() {
        double z = 0.0;
        for (int r = 0; r < m; r++) {
            double cb = cost[basis[r]];
            if (cb != 0.0) z += cb * xB[r];
        }
        for (int v = 0; v < total; v++) {
            if (state[v] == 1 && cost[v] != 0.0) z += cost[v] * hiOf(v);
        }
        if (z < lastZ - 1.0e-13 * (1.0 + Math.abs(lastZ))) {
            lastZ = z;
            stall = 0;
        } else {
            if (++stall >= STALL_LIMIT) bland = true;
        }
    }

    private void refactor() {
        double[][] bm = new double[m][m];
        double[] col = new double[m];
        for (int j = 0; j < m; j++) {
            column(basis[j], col);
            for (int r = 0; r < m; r++) bm[r][j] = col[r];
        }
        double[][] inv = new double[m][m];
        for (int r = 0; r < m; r++) inv[r][r] = 1.0;
        for (int c = 0; c < m; c++) {
            int p = c;
            for (int r = c + 1; r < m; r++) {
                if (Math.abs(bm[r][c]) > Math.abs(bm[p][c])) p = r;
            }
            if (p != c) {
                double[] tmp = bm[p]; bm[p] = bm[c]; bm[c] = tmp;
                tmp = inv[p]; inv[p] = inv[c]; inv[c] = tmp;
            }
            double d = bm[c][c];
            if (d == 0.0) d = 1.0e-300;
            double invd = 1.0 / d;
            for (int k = 0; k < m; k++) {
                bm[c][k] *= invd;
                inv[c][k] *= invd;
            }
            for (int r = 0; r < m; r++) {
                if (r == c) continue;
                double f = bm[r][c];
                if (f == 0.0) continue;
                for (int k = 0; k < m; k++) {
                    bm[r][k] -= f * bm[c][k];
                    inv[r][k] -= f * inv[c][k];
                }
            }
        }
        for (int r = 0; r < m; r++) System.arraycopy(inv[r], 0, binv[r], 0, m);
        double[] t = new double[m];
        System.arraycopy(rhs, 0, t, 0, m);
        for (int v = 0; v < total; v++) {
            if (state[v] != 1) continue;
            double xv = hiOf(v);
            if (xv == 0.0) continue;
            if (v < n) {
                for (int r = 0; r < m; r++) t[r] -= a[r][v] * xv;
            } else if (v < 2 * n) {
                int u = v - n;
                for (int r = 0; r < m; r++) t[r] += a[r][u] * xv;
            } else if (v == sVar) {
                for (int r = 0; r < m; r++) t[r] += xv;
            } else {
                t[v - 2 * n - 1] -= xv;
            }
        }
        for (int r = 0; r < m; r++) {
            double s = 0.0;
            for (int k = 0; k < m; k++) s += binv[r][k] * t[k];
            xB[r] = s;
        }
    }

    private double valueOf(int v) {
        if (state[v] == 2) {
            for (int r = 0; r < m; r++) {
                if (basis[r] == v) return xB[r];
            }
            return 0.0;
        }
        return state[v] == 1 ? hiOf(v) : loOf(v);
    }

    private double[] extractD() {
        double[] d = new double[n];
        for (int v = 0; v < n; v++) {
            d[v] = valueOf(v) - valueOf(n + v);
        }
        return d;
    }
}
