package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.List;
import java.util.Map;

public final class DiskSocpKernel {

    public static boolean DEBUG = false;

    private static final double MU0 = 1.0;
    private static final double MU_REDUCE = 0.2;
    private static final int MAX_OUTER = 80;
    private static final int MAX_INNER = 50;
    private static final double INNER_TOL = 1.0e-10;
    private static final double FRAC = 0.99;
    private static final double INF = 1.0e18;
    private static final double LAMBDA_CAP = 1.0e12;
    private static final double ARMIJO = 1.0e-4;
    private static final double REG_FACTOR = 1.0e-4;
    private static final double WARM_MIN = 1.0e-8;

    public static final int FAIL_NONE = 0;
    public static final int FAIL_CHOLESKY = 2;
    public static final int FAIL_NAN = 3;
    public static final int FAIL_UNBOUNDED = 4;
    public static final int FAIL_DET = 5;

    private final int n;
    private final int m;
    private final int p;
    private final double[] cx;
    private final double[] cz;
    private final double[] mMag;
    private final int[] axis;
    private final double[][] coefX;
    private final double[][] coefZ;
    private final int[] lastCoupled;
    private final double[] bPrime;
    private final boolean[] eq;
    private final int[] orthOf;
    private final double[] scale;
    private final double[] p0coef;
    private final String[] rowName;
    private final CostateDualSolver.FreeP0 freeP0;
    private final Map<String, Double> warmLambda;

    private int failCode = FAIL_NONE;
    private int failIter;
    private double failMu = Double.NaN;
    private double[] uRefX;
    private double[] uRefZ;

    private DiskSocpKernel(int n, double[] cx, double[] cz, double[] mMag, List<JumpLinearModel.Wall> walls,
                           CostateDualSolver.FreeP0 freeP0, List<ChordRow> chords,
                           Map<String, Double> warmLambda) {
        this.n = n;
        int mw = walls.size();
        int mc = chords == null ? 0 : chords.size();
        this.m = mw + mc;
        this.cx = cx;
        this.cz = cz;
        this.mMag = mMag;
        this.freeP0 = freeP0;
        this.warmLambda = warmLambda;
        this.axis = new int[m];
        this.coefX = new double[m][];
        this.coefZ = new double[m][];
        this.lastCoupled = new int[m];
        this.bPrime = new double[m];
        this.eq = new boolean[m];
        this.orthOf = new int[m];
        this.scale = new double[m];
        this.p0coef = new double[m];
        this.rowName = new String[m];
        int po = 0;
        for (int j = 0; j < mw; j++) {
            JumpLinearModel.Wall w = walls.get(j);
            axis[j] = w.axis;
            eq[j] = w.eq;
            orthOf[j] = w.eq ? -1 : po++;
            rowName[j] = w.name;
            int lim = Math.min(w.coef.length, n);
            double s = Math.abs(w.bPrime);
            for (int t = 0; t < lim; t++) s = Math.max(s, Math.abs(w.coef[t]));
            if (s <= 0.0) s = 1.0;
            scale[j] = s;
            double[] cj = new double[n];
            int last = -1;
            for (int t = 0; t < lim; t++) {
                cj[t] = w.coef[t] / s;
                if (cj[t] != 0.0) last = t;
            }
            if (w.axis == 0) {
                coefX[j] = cj;
                coefZ[j] = null;
            } else {
                coefX[j] = null;
                coefZ[j] = cj;
            }
            bPrime[j] = w.bPrime / s;
            lastCoupled[j] = last;
            p0coef[j] = w.p0coef / s;
        }
        for (int q = 0; q < mc; q++) {
            int j = mw + q;
            ChordRow ch = chords.get(q);
            axis[j] = -1;
            eq[j] = false;
            orthOf[j] = po++;
            rowName[j] = ch.name;
            double s = Math.max(Math.abs(ch.rhs), Math.max(Math.abs(ch.ax), Math.abs(ch.az)));
            if (s <= 0.0) s = 1.0;
            scale[j] = s;
            double[] cxr = new double[n];
            double[] czr = new double[n];
            cxr[ch.tick] = -ch.ax / s;
            czr[ch.tick] = -ch.az / s;
            coefX[j] = cxr;
            coefZ[j] = czr;
            bPrime[j] = -ch.rhs / s;
            lastCoupled[j] = ch.tick;
            p0coef[j] = 0.0;
        }
        this.p = po;
    }

    public static final class ChordRow {
        public final int tick;
        public final double ax;
        public final double az;
        public final double rhs;
        public final String name;

        public ChordRow(int tick, double ax, double az, double rhs, String name) {
            this.tick = tick;
            this.ax = ax;
            this.az = az;
            this.rhs = rhs;
            this.name = name;
        }
    }

    public static final class Result {
        public final double[] gx;
        public final double[] gz;
        public final double[] ux;
        public final double[] uz;
        public final double[] lambda;
        public final double value;
        public final boolean converged;
        public final int iters;
        public final double gap;
        public final double dvx;
        public final double dvz;
        public double[] uxRef;
        public double[] uzRef;

        Result(double[] gx, double[] gz, double[] ux, double[] uz, double[] lambda, double value,
               boolean converged, int iters, double gap, double dvx, double dvz) {
            this.gx = gx;
            this.gz = gz;
            this.ux = ux;
            this.uz = uz;
            this.lambda = lambda;
            this.value = value;
            this.converged = converged;
            this.iters = iters;
            this.gap = gap;
            this.dvx = dvx;
            this.dvz = dvz;
        }
    }

    public static final class Outcome {
        public final Result result;
        public final int failCode;
        public final int failIter;
        public final double failMu;

        Outcome(Result result, int failCode, int failIter, double failMu) {
            this.result = result;
            this.failCode = failCode;
            this.failIter = failIter;
            this.failMu = failMu;
        }

        public boolean infeasible() {
            return failCode == FAIL_UNBOUNDED;
        }
    }

    public static Result solve(int n, double[] cx, double[] cz, double[] mMag,
                               List<JumpLinearModel.Wall> walls) {
        return solve(n, cx, cz, mMag, walls, null);
    }

    public static Result solve(int n, double[] cx, double[] cz, double[] mMag,
                               List<JumpLinearModel.Wall> walls, CostateDualSolver.FreeP0 freeP0) {
        DiskSocpKernel k = new DiskSocpKernel(n, cx, cz, mMag, walls, freeP0, null, null);
        Result r = k.run();
        return k.failCode != FAIL_NONE ? null : r;
    }

    public static Outcome solveChords(int n, double[] cx, double[] cz, double[] mMag,
                                      List<JumpLinearModel.Wall> walls, CostateDualSolver.FreeP0 freeP0,
                                      List<ChordRow> chords, Map<String, Double> warmLambda) {
        DiskSocpKernel k = new DiskSocpKernel(n, cx, cz, mMag, walls, freeP0, chords, warmLambda);
        Result r = k.run();
        return new Outcome(r, k.failCode, k.failIter, k.failMu);
    }

    private Result run() {
        if (m == 0) return trivial();

        double[] lambda = new double[m];
        double[] tau = new double[n];
        double[] gx = new double[n];
        double[] gz = new double[n];
        for (int j = 0; j < m; j++) {
            double init = eq[j] ? 0.0 : 1.0;
            if (warmLambda != null) {
                Double w = warmLambda.get(rowName[j]);
                if (w != null) {
                    double ws = w * scale[j];
                    init = eq[j] ? ws : Math.max(ws, WARM_MIN);
                }
            }
            lambda[j] = init;
        }
        costates(lambda, gx, gz);
        for (int t = 0; t < n; t++) tau[t] = Math.hypot(gx[t], gz[t]) + 1.0;

        double[] gLam = new double[m];
        double[] gTau = new double[n];
        double[][] S = new double[m][m];
        double[][] L = new double[m][m];
        double[] Mlt = new double[m * n];
        double[] Dtt = new double[n];
        double[] dLam = new double[m];
        double[] dTau = new double[n];
        double[] rhsLam = new double[m];
        double[] bestLambda = null;
        double[] bestTau = null;
        double bestMu = Double.NaN;

        double nu = 2.0 * n + p;
        double mu = centeringMu(lambda, tau, gx, gz);
        boolean converged = false;
        boolean aborted = false;
        int iters = 0;
        int numericFailOuters = 0;
        outer:
        for (int outer = 0; outer < MAX_OUTER && !converged; outer++) {
            boolean numericFail = false;
            for (int inner = 0; inner < MAX_INNER; inner++) {
                iters++;
                gradient(lambda, tau, gx, gz, mu, gLam, gTau);
                if (!assembleHessian(lambda, tau, gx, gz, mu, S, Mlt, Dtt)) {
                    fail(FAIL_DET, iters, mu);
                    numericFail = true;
                    break;
                }
                if (!factor(S, L)) {
                    fail(FAIL_CHOLESKY, iters, mu);
                    numericFail = true;
                    break;
                }
                newtonDir(gLam, gTau, Mlt, Dtt, L, rhsLam, dLam, dTau);
                double dec = 0.0;
                for (int j = 0; j < m; j++) dec -= gLam[j] * dLam[j];
                for (int t = 0; t < n; t++) dec -= gTau[t] * dTau[t];
                if (dec <= INNER_TOL) break;
                double step = lineSearch(lambda, tau, gx, gz, mu, gLam, gTau, dLam, dTau, dec);
                if (DEBUG) System.out.printf("  outer=%d inner=%d mu=%.3e dec=%.3e step=%.3e%n", outer, inner, mu, dec, step);
                if (step <= 0.0) break;
                for (int j = 0; j < m; j++) lambda[j] += step * dLam[j];
                for (int t = 0; t < n; t++) tau[t] += step * dTau[t];
                costates(lambda, gx, gz);
                for (int j = 0; j < m; j++) {
                    if (Double.isNaN(lambda[j])) {
                        fail(FAIL_NAN, iters, mu);
                        aborted = true;
                        break outer;
                    }
                    if (Math.abs(lambda[j]) > LAMBDA_CAP) {
                        fail(FAIL_UNBOUNDED, iters, mu);
                        aborted = true;
                        break outer;
                    }
                }
            }
            bestLambda = lambda.clone();
            bestTau = tau.clone();
            bestMu = mu;
            if (uRefX == null && mu * nu <= 1.0e-4) {
                uRefX = new double[n];
                uRefZ = new double[n];
                for (int t = 0; t < n; t++) {
                    double w = tau[t] > 0.0 ? mMag[t] / tau[t] : 0.0;
                    uRefX[t] = w * gx[t];
                    uRefZ[t] = w * gz[t];
                }
            }
            if (numericFail) {
                numericFailOuters++;
                if (numericFailOuters >= 3) break;
            } else {
                numericFailOuters = 0;
                if (mu * nu <= 1.0e-11) { converged = true; break; }
            }
            mu *= MU_REDUCE;
        }
        if (converged) {
            failCode = FAIL_NONE;
            failIter = 0;
            failMu = Double.NaN;
        }
        if (aborted) {
            if (failCode == FAIL_UNBOUNDED || bestLambda == null) return null;
            lambda = bestLambda;
            tau = bestTau;
            mu = bestMu;
            costates(lambda, gx, gz);
        }
        Result r = emit(lambda, tau, gx, gz, converged, iters, mu * nu);
        double lb = primalLowBound();
        if (r.value < lb - 1.0e-6 * (1.0 + Math.abs(lb))) {
            fail(FAIL_UNBOUNDED, iters, mu);
        }
        return r;
    }

    private double primalLowBound() {
        double lb = 0.0;
        for (int t = 0; t < n; t++) lb -= mMag[t] * Math.hypot(cx[t], cz[t]);
        if (freeP0 != null) {
            lb -= Math.abs(freeP0.objDevX) * Math.max(Math.abs(freeP0.dvLoX), Math.abs(freeP0.dvHiX));
            lb -= Math.abs(freeP0.objDevZ) * Math.max(Math.abs(freeP0.dvLoZ), Math.abs(freeP0.dvHiZ));
        }
        return lb;
    }

    private void fail(int code, int iter, double mu) {
        this.failCode = code;
        this.failIter = iter;
        this.failMu = mu;
    }

    private Result emit(double[] lambda, double[] tau, double[] gx, double[] gz, boolean converged,
                        int iters, double gap) {
        double[] ux = new double[n];
        double[] uz = new double[n];
        double value = 0.0;
        for (int t = 0; t < n; t++) {
            double w = tau[t] > 0.0 ? mMag[t] / tau[t] : 0.0;
            ux[t] = w * gx[t];
            uz[t] = w * gz[t];
            value += mMag[t] * Math.hypot(gx[t], gz[t]);
        }
        for (int j = 0; j < m; j++) value += lambda[j] * bPrime[j];
        double dvx = 0.0;
        double dvz = 0.0;
        if (freeP0 != null) {
            double hx = hAxis(lambda, 0);
            double hz = hAxis(lambda, 1);
            value += supportOf(hx, 0) + supportOf(hz, 1);
            dvx = deltaOf(hx, 0);
            dvz = deltaOf(hz, 1);
        }
        double[] lambdaOut = new double[m];
        for (int j = 0; j < m; j++) lambdaOut[j] = lambda[j] / scale[j];
        Result r = new Result(gx, gz, ux, uz, lambdaOut, value, converged, iters, gap, dvx, dvz);
        r.uxRef = uRefX;
        r.uzRef = uRefZ;
        return r;
    }

    public String rowNameAt(int j) {
        return rowName[j];
    }

    private Result trivial() {
        double[] gx = cx.clone();
        double[] gz = cz.clone();
        double[] ux = new double[n];
        double[] uz = new double[n];
        double value = 0.0;
        for (int t = 0; t < n; t++) {
            double nrm = Math.hypot(gx[t], gz[t]);
            value += mMag[t] * nrm;
            if (nrm > 0.0) {
                ux[t] = mMag[t] * gx[t] / nrm;
                uz[t] = mMag[t] * gz[t] / nrm;
            }
        }
        double dvx = 0.0;
        double dvz = 0.0;
        if (freeP0 != null) {
            double hx = hAxis(EMPTY, 0);
            double hz = hAxis(EMPTY, 1);
            value += supportOf(hx, 0) + supportOf(hz, 1);
            dvx = deltaOf(hx, 0);
            dvz = deltaOf(hz, 1);
        }
        return new Result(gx, gz, ux, uz, new double[0], value, true, 0, 0.0, dvx, dvz);
    }

    private static final double[] EMPTY = new double[0];

    private double hAxis(double[] lambda, int a) {
        double h = a == 0 ? freeP0.objDevX : freeP0.objDevZ;
        for (int j = 0; j < Math.min(lambda.length, m); j++) {
            if (axis[j] == a) h += lambda[j] * p0coef[j];
        }
        return h;
    }

    private double deltaOf(double h, int a) {
        double lo = a == 0 ? freeP0.dvLoX : freeP0.dvLoZ;
        double hi = a == 0 ? freeP0.dvHiX : freeP0.dvHiZ;
        double d = h / freeP0.smooth;
        return d < lo ? lo : (d > hi ? hi : d);
    }

    private double supportOf(double h, int a) {
        double d = deltaOf(h, a);
        return h * d - 0.5 * freeP0.smooth * d * d;
    }

    private double supportCurv(double h, int a) {
        double lo = a == 0 ? freeP0.dvLoX : freeP0.dvLoZ;
        double hi = a == 0 ? freeP0.dvHiX : freeP0.dvHiZ;
        double d = h / freeP0.smooth;
        return (d > lo && d < hi) ? 1.0 / freeP0.smooth : 0.0;
    }

    private double centeringMu(double[] lambda, double[] tau, double[] gx, double[] gz) {
        double num = 0.0;
        double den = 0.0;
        for (int t = 0; t < n; t++) {
            double det = tau[t] * tau[t] - gx[t] * gx[t] - gz[t] * gz[t];
            double h = 2.0 * tau[t] / det;
            num += mMag[t] * h;
            den += h * h;
        }
        for (int j = 0; j < m; j++) {
            double[] cxj = coefX[j];
            double[] czj = coefZ[j];
            int last = lastCoupled[j];
            double h = 0.0;
            for (int t = 0; t <= last; t++) {
                double det = 0.0;
                boolean have = false;
                if (cxj != null && cxj[t] != 0.0) {
                    det = tau[t] * tau[t] - gx[t] * gx[t] - gz[t] * gz[t];
                    have = true;
                    h += 2.0 * cxj[t] * gx[t] / det;
                }
                if (czj != null && czj[t] != 0.0) {
                    if (!have) det = tau[t] * tau[t] - gx[t] * gx[t] - gz[t] * gz[t];
                    h += 2.0 * czj[t] * gz[t] / det;
                }
            }
            if (orthOf[j] >= 0) h += 1.0 / lambda[j];
            num += bPrime[j] * h;
            den += h * h;
        }
        double mu = den > 0.0 ? num / den : MU0;
        if (!(mu > 0.0) || Double.isNaN(mu)) mu = MU0;
        return Math.max(mu, 1.0e-8);
    }

    private void costates(double[] lambda, double[] gx, double[] gz) {
        for (int t = 0; t < n; t++) { gx[t] = cx[t]; gz[t] = cz[t]; }
        for (int j = 0; j < m; j++) {
            double lj = lambda[j];
            if (lj == 0.0) continue;
            double[] cxj = coefX[j];
            double[] czj = coefZ[j];
            int last = lastCoupled[j];
            if (cxj != null) for (int t = 0; t <= last; t++) gx[t] -= lj * cxj[t];
            if (czj != null) for (int t = 0; t <= last; t++) gz[t] -= lj * czj[t];
        }
    }

    private void gradient(double[] lambda, double[] tau, double[] gx, double[] gz, double mu,
                          double[] gLam, double[] gTau) {
        for (int t = 0; t < n; t++) {
            double det = tau[t] * tau[t] - gx[t] * gx[t] - gz[t] * gz[t];
            gTau[t] = mMag[t] - mu * 2.0 * tau[t] / det;
        }
        for (int j = 0; j < m; j++) {
            double[] cxj = coefX[j];
            double[] czj = coefZ[j];
            int last = lastCoupled[j];
            double acc = bPrime[j];
            for (int t = 0; t <= last; t++) {
                double det = 0.0;
                boolean have = false;
                if (cxj != null && cxj[t] != 0.0) {
                    det = tau[t] * tau[t] - gx[t] * gx[t] - gz[t] * gz[t];
                    have = true;
                    acc -= mu * 2.0 * cxj[t] * gx[t] / det;
                }
                if (czj != null && czj[t] != 0.0) {
                    if (!have) det = tau[t] * tau[t] - gx[t] * gx[t] - gz[t] * gz[t];
                    acc -= mu * 2.0 * czj[t] * gz[t] / det;
                }
            }
            if (orthOf[j] >= 0) acc -= mu / lambda[j];
            gLam[j] = acc + REG_FACTOR * mu * lambda[j];
        }
        if (freeP0 != null) {
            double dsx = deltaOf(hAxis(lambda, 0), 0);
            double dsz = deltaOf(hAxis(lambda, 1), 1);
            for (int j = 0; j < m; j++) gLam[j] += p0coef[j] * (axis[j] == 0 ? dsx : (axis[j] == 1 ? dsz : 0.0));
        }
    }

    private boolean assembleHessian(double[] lambda, double[] tau, double[] gx, double[] gz, double mu,
                                    double[][] S, double[] Mlt, double[] Dtt) {
        for (int j = 0; j < m; j++) java.util.Arrays.fill(S[j], 0.0);
        java.util.Arrays.fill(Mlt, 0.0);
        for (int t = 0; t < n; t++) {
            double det = tau[t] * tau[t] - gx[t] * gx[t] - gz[t] * gz[t];
            if (det <= 0.0) return false;
            Dtt[t] = 2.0 * mu * (tau[t] * tau[t] + gx[t] * gx[t] + gz[t] * gz[t]) / (det * det);
        }
        for (int j = 0; j < m; j++) {
            S[j][j] += REG_FACTOR * mu;
            if (orthOf[j] >= 0) S[j][j] += mu / (lambda[j] * lambda[j]);
        }
        if (freeP0 != null) {
            double curvX = supportCurv(hAxis(lambda, 0), 0);
            double curvZ = supportCurv(hAxis(lambda, 1), 1);
            for (int j = 0; j < m; j++) {
                if (axis[j] < 0 || p0coef[j] == 0.0) continue;
                double cv = axis[j] == 0 ? curvX : curvZ;
                if (cv == 0.0) continue;
                for (int k = 0; k < m; k++) {
                    if (axis[k] != axis[j] || p0coef[k] == 0.0) continue;
                    S[j][k] += p0coef[j] * p0coef[k] * cv;
                }
            }
        }
        for (int t = 0; t < n; t++) {
            double det = tau[t] * tau[t] - gx[t] * gx[t] - gz[t] * gz[t];
            double invd = 1.0 / det;
            double invd2 = invd * invd;
            for (int j = 0; j < m; j++) {
                if (t > lastCoupled[j]) continue;
                double ajx = coefX[j] != null ? coefX[j][t] : 0.0;
                double ajz = coefZ[j] != null ? coefZ[j][t] : 0.0;
                if (ajx == 0.0 && ajz == 0.0) continue;
                double ajg = 0.0;
                if (ajx != 0.0) ajg += ajx * gx[t];
                if (ajz != 0.0) ajg += ajz * gz[t];
                Mlt[j * n + t] = 4.0 * mu * tau[t] * ajg * invd2;
                for (int k = 0; k < m; k++) {
                    if (t > lastCoupled[k]) continue;
                    double akx = coefX[k] != null ? coefX[k][t] : 0.0;
                    double akz = coefZ[k] != null ? coefZ[k][t] : 0.0;
                    if (akx == 0.0 && akz == 0.0) continue;
                    double akg = 0.0;
                    if (akx != 0.0) akg += akx * gx[t];
                    if (akz != 0.0) akg += akz * gz[t];
                    double dotA = 0.0;
                    if (ajx != 0.0 && akx != 0.0) dotA += ajx * akx;
                    if (ajz != 0.0 && akz != 0.0) dotA += ajz * akz;
                    S[j][k] += 2.0 * mu * (dotA * invd + 2.0 * ajg * akg * invd2);
                }
            }
        }
        for (int j = 0; j < m; j++) {
            for (int t = 0; t < n; t++) {
                double mlt = Mlt[j * n + t];
                if (mlt == 0.0) continue;
                double f = mlt / Dtt[t];
                for (int k = 0; k < m; k++) {
                    double mlk = Mlt[k * n + t];
                    if (mlk != 0.0) S[j][k] -= f * mlk;
                }
            }
        }
        return true;
    }

    private void newtonDir(double[] gLam, double[] gTau, double[] Mlt, double[] Dtt, double[][] L,
                           double[] rhsLam, double[] dLam, double[] dTau) {
        for (int j = 0; j < m; j++) {
            double acc = -gLam[j];
            for (int t = 0; t < n; t++) {
                double mlt = Mlt[j * n + t];
                if (mlt != 0.0) acc -= mlt * (-gTau[t] / Dtt[t]);
            }
            rhsLam[j] = acc;
            dLam[j] = acc;
        }
        solveFactored(L, dLam);
        for (int t = 0; t < n; t++) {
            double acc = -gTau[t];
            for (int j = 0; j < m; j++) {
                double mlt = Mlt[j * n + t];
                if (mlt != 0.0) acc -= mlt * dLam[j];
            }
            dTau[t] = acc / Dtt[t];
        }
    }

    private double lineSearch(double[] lambda, double[] tau, double[] gx, double[] gz, double mu,
                              double[] gLam, double[] gTau, double[] dLam, double[] dTau, double dec) {
        double amax = INF;
        for (int j = 0; j < m; j++) {
            if (orthOf[j] >= 0 && dLam[j] < 0.0) amax = Math.min(amax, -lambda[j] / dLam[j]);
        }
        double[] dg = new double[2];
        for (int t = 0; t < n; t++) {
            dgAt(t, dLam, dg);
            amax = Math.min(amax, maxStepSoc(tau[t], gx[t], gz[t], dTau[t], dg[0], dg[1]));
        }
        double a = Math.min(1.0, FRAC * amax);
        double phi0 = barrier(lambda, tau, gx, gz, mu);
        double[] tmpGx = new double[n];
        double[] tmpGz = new double[n];
        for (int ls = 0; ls < 40; ls++) {
            boolean ok = true;
            for (int j = 0; j < m && ok; j++) {
                if (orthOf[j] >= 0 && lambda[j] + a * dLam[j] <= 0.0) ok = false;
            }
            if (ok) {
                for (int t = 0; t < n; t++) { tmpGx[t] = cx[t]; tmpGz[t] = cz[t]; }
                for (int j = 0; j < m; j++) {
                    double lj = lambda[j] + a * dLam[j];
                    if (lj == 0.0) continue;
                    double[] cxj = coefX[j];
                    double[] czj = coefZ[j];
                    int last = lastCoupled[j];
                    if (cxj != null) for (int t = 0; t <= last; t++) tmpGx[t] -= lj * cxj[t];
                    if (czj != null) for (int t = 0; t <= last; t++) tmpGz[t] -= lj * czj[t];
                }
                boolean feas = true;
                for (int t = 0; t < n && feas; t++) {
                    double tt = tau[t] + a * dTau[t];
                    if (tt * tt - tmpGx[t] * tmpGx[t] - tmpGz[t] * tmpGz[t] <= 0.0) feas = false;
                }
                if (feas) {
                    double phi = barrierWith(lambda, tau, a, dLam, dTau, tmpGx, tmpGz, mu);
                    if (phi <= phi0 - ARMIJO * a * dec) return a;
                }
            }
            a *= 0.5;
        }
        return 0.0;
    }

    private void dgAt(int t, double[] dLam, double[] dg) {
        double d0 = 0.0, d1 = 0.0;
        for (int j = 0; j < m; j++) {
            if (t > lastCoupled[j]) continue;
            double[] cxj = coefX[j];
            double[] czj = coefZ[j];
            if (cxj != null && cxj[t] != 0.0) d0 -= cxj[t] * dLam[j];
            if (czj != null && czj[t] != 0.0) d1 -= czj[t] * dLam[j];
        }
        dg[0] = d0;
        dg[1] = d1;
    }

    private double barrier(double[] lambda, double[] tau, double[] gx, double[] gz, double mu) {
        double v = 0.0;
        double reg = REG_FACTOR * mu;
        for (int j = 0; j < m; j++) v += bPrime[j] * lambda[j] + 0.5 * reg * lambda[j] * lambda[j];
        for (int t = 0; t < n; t++) {
            v += mMag[t] * tau[t];
            double det = tau[t] * tau[t] - gx[t] * gx[t] - gz[t] * gz[t];
            v -= mu * Math.log(det);
        }
        for (int j = 0; j < m; j++) if (orthOf[j] >= 0) v -= mu * Math.log(lambda[j]);
        if (freeP0 != null) v += supportOf(hAxis(lambda, 0), 0) + supportOf(hAxis(lambda, 1), 1);
        return v;
    }

    private double barrierWith(double[] lambda, double[] tau, double a, double[] dLam, double[] dTau,
                               double[] gx, double[] gz, double mu) {
        double v = 0.0;
        double reg = REG_FACTOR * mu;
        for (int j = 0; j < m; j++) {
            double lj = lambda[j] + a * dLam[j];
            v += bPrime[j] * lj + 0.5 * reg * lj * lj;
        }
        for (int t = 0; t < n; t++) {
            double tt = tau[t] + a * dTau[t];
            v += mMag[t] * tt;
            double det = tt * tt - gx[t] * gx[t] - gz[t] * gz[t];
            v -= mu * Math.log(det);
        }
        for (int j = 0; j < m; j++) if (orthOf[j] >= 0) v -= mu * Math.log(lambda[j] + a * dLam[j]);
        if (freeP0 != null) {
            double hx = freeP0.objDevX;
            double hz = freeP0.objDevZ;
            for (int j = 0; j < m; j++) {
                double lj = lambda[j] + a * dLam[j];
                if (axis[j] == 0) hx += lj * p0coef[j];
                else if (axis[j] == 1) hz += lj * p0coef[j];
            }
            v += supportOf(hx, 0) + supportOf(hz, 1);
        }
        return v;
    }

    private static double maxStepSoc(double v0, double v1, double v2, double d0, double d1, double d2) {
        double a = d0 * d0 - d1 * d1 - d2 * d2;
        double b = v0 * d0 - v1 * d1 - v2 * d2;
        double c = v0 * v0 - v1 * v1 - v2 * v2;
        double step = INF;
        if (a < 0.0) {
            double disc = b * b - a * c;
            double sq = Math.sqrt(disc);
            step = Math.min(step, minPos((-b - sq) / a, (-b + sq) / a));
        } else if (a > 0.0) {
            double disc = b * b - a * c;
            if (disc > 0.0) {
                double sq = Math.sqrt(disc);
                step = Math.min(step, minPos((-b - sq) / a, (-b + sq) / a));
            }
        } else if (b < 0.0) {
            step = Math.min(step, -c / (2.0 * b));
        }
        if (d0 < 0.0) step = Math.min(step, -v0 / d0);
        return step;
    }

    private static double minPos(double r1, double r2) {
        double best = INF;
        if (r1 > 0.0 && r1 < best) best = r1;
        if (r2 > 0.0 && r2 < best) best = r2;
        return best;
    }

    private boolean factor(double[][] S, double[][] L) {
        double maxDiag = 0.0;
        double minDiag = Double.POSITIVE_INFINITY;
        for (int j = 0; j < m; j++) {
            maxDiag = Math.max(maxDiag, S[j][j]);
            minDiag = Math.min(minDiag, S[j][j]);
        }
        double jitter = 0.0;
        for (int attempt = 0; attempt < 12; attempt++) {
            if (cholesky(S, L, jitter)) return true;
            jitter = jitter == 0.0 ? 1.0e-12 * (maxDiag + 1.0) : jitter * 10.0;
        }
        if (DEBUG) {
            double probe = jitter;
            int extra = 0;
            while (extra < 20 && !cholesky(S, L, probe)) {
                probe *= 10.0;
                extra++;
            }
            System.out.printf("  factor FAIL m=%d minDiag=%.3e maxDiag=%.3e lastJitter=%.3e passJitter=%.3e%n",
                    m, minDiag, maxDiag, jitter, extra < 20 ? probe : Double.NaN);
        }
        return false;
    }

    private boolean cholesky(double[][] S, double[][] L, double jitter) {
        for (int i = 0; i < m; i++) {
            for (int j = 0; j <= i; j++) {
                double s = S[i][j] + (i == j ? jitter : 0.0);
                for (int k = 0; k < j; k++) s -= L[i][k] * L[j][k];
                if (i == j) {
                    if (s <= 0.0) return false;
                    L[i][i] = Math.sqrt(s);
                } else {
                    L[i][j] = s / L[j][j];
                }
            }
        }
        return true;
    }

    private void solveFactored(double[][] L, double[] x) {
        for (int i = 0; i < m; i++) {
            double s = x[i];
            for (int k = 0; k < i; k++) s -= L[i][k] * x[k];
            x[i] = s / L[i][i];
        }
        for (int i = m - 1; i >= 0; i--) {
            double s = x[i];
            for (int k = i + 1; k < m; k++) s -= L[k][i] * x[k];
            x[i] = s / L[i][i];
        }
    }
}
