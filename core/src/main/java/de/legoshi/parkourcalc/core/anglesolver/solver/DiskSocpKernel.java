package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.Arrays;
import java.util.List;

public final class DiskSocpKernel {

    public static boolean DEBUG = false;

    private static final int MAX_ITERS = 80;
    private static final double GAP_TOL = 1.0e-10;
    private static final double RESIDUAL_TOL = 1.0e-9;
    private static final double STALL_TOL = 1.0e-8;
    private static final int STALL_ITERS = 3;
    private static final double STEP_FRACTION = 0.99;
    private static final double MIN_STEP = 1.0e-12;
    private static final double UNBOUNDED_MARGIN = 1.0e-6;
    private static final double INF = 1.0e18;
    private static final double LAMBDA_CAP = 1.0e12;

    private static final int JOINT = 0;
    private static final int PRIMAL = 1;
    private static final int DUAL = 2;

    public static final int FAIL_NONE = 0;
    public static final int FAIL_CHOLESKY = 2;
    public static final int FAIL_NAN = 3;
    public static final int FAIL_UNBOUNDED = 4;
    public static final int FAIL_DET = 5;
    public static final int FAIL_CUTOFF = 6;
    public static final int FAIL_STALLED = 7;

    private final int n;
    private final int m;
    private final double[] cx;
    private final double[] cz;
    private final double[] mMag;
    private final int[] axis;
    private final double[][] coefX;
    private final double[][] coefZ;
    private final int[] lastCoupled;
    private final double[] bPrime;
    private final boolean[] eq;
    private final double[] scale;
    private final double[] p0coef;
    private final int[][] rowsAt;
    private final double[][] coefXAt;
    private final double[][] coefZAt;
    private final CostateDualSolver.FreeP0 freeP0;
    private final boolean[] cone;
    private final boolean[] boxOn = new boolean[2];
    private final double[] boxLo = new double[2];
    private final double[] boxHi = new double[2];
    private final double complementarityCount;

    private int failCode = FAIL_NONE;
    private int failIter;
    private double failMu = Double.NaN;
    private double cutoff = Double.NEGATIVE_INFINITY;

    private double[] lambda;
    private double[] slack;
    private double[] tau;
    private double[] gx;
    private double[] gz;
    private double[] x1;
    private double[] x2;
    private final double[] boxV = new double[2];
    private final double[] boxOmega = new double[2];

    private double[] residual;
    private double residualMax;
    private double multiplierResidual;
    private double gap;

    private double[] h00;
    private double[] ratio1;
    private double[] ratio2;
    private double[] k11;
    private double[] k12;
    private double[] k22;
    private double[] beta;
    private double[] vb0;
    private double[] vb1;
    private double[] vb2;
    private double[] nu0;
    private double[] nu1;
    private double[] nu2;
    private final double[] wPlus = new double[2];
    private final double[] wMinus = new double[2];
    private final double[] piPlus = new double[2];
    private final double[] piMinus = new double[2];
    private final double[] kappa = new double[2];
    private double[][] schur;
    private double[][] chol;

    private double[] q0;
    private double[] q1;
    private double[] q2;
    private double[] rowTarget;
    private final double[] targetPlus = new double[2];
    private final double[] targetMinus = new double[2];

    private DiskSocpKernel(int n, double[] cx, double[] cz, double[] mMag, List<JumpLinearModel.Wall> walls,
                           CostateDualSolver.FreeP0 freeP0, List<ChordRow> chords) {
        this.n = n;
        int mw = walls.size();
        int mc = chords == null ? 0 : chords.size();
        this.m = mw + mc;
        this.cx = cx;
        this.cz = cz;
        this.mMag = mMag;
        this.freeP0 = freeP0;
        this.axis = new int[m];
        this.coefX = new double[m][];
        this.coefZ = new double[m][];
        this.lastCoupled = new int[m];
        this.bPrime = new double[m];
        this.eq = new boolean[m];
        this.scale = new double[m];
        this.p0coef = new double[m];
        int inequalities = 0;
        for (int j = 0; j < mw; j++) {
            JumpLinearModel.Wall w = walls.get(j);
            axis[j] = w.boxAxis;
            eq[j] = w.eq;
            if (!w.eq) inequalities++;
            double[] wx = w.axis == 2 ? w.coefX : (w.axis == 0 ? w.coef : null);
            double[] wz = w.axis == 2 ? w.coefZ : (w.axis == 0 ? null : w.coef);
            double s = Math.abs(w.bPrime);
            if (wx != null) for (int t = 0; t < Math.min(wx.length, n); t++) s = Math.max(s, Math.abs(wx[t]));
            if (wz != null) for (int t = 0; t < Math.min(wz.length, n); t++) s = Math.max(s, Math.abs(wz[t]));
            if (s <= 0.0) s = 1.0;
            scale[j] = s;
            int last = -1;
            coefX[j] = wx == null ? null : new double[n];
            coefZ[j] = wz == null ? null : new double[n];
            if (wx != null) {
                for (int t = 0; t < Math.min(wx.length, n); t++) {
                    coefX[j][t] = wx[t] / s;
                    if (coefX[j][t] != 0.0) last = Math.max(last, t);
                }
            }
            if (wz != null) {
                for (int t = 0; t < Math.min(wz.length, n); t++) {
                    coefZ[j][t] = wz[t] / s;
                    if (coefZ[j][t] != 0.0) last = Math.max(last, t);
                }
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
            inequalities++;
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
        this.rowsAt = rowsPerTick();
        this.coefXAt = new double[n][];
        this.coefZAt = new double[n][];
        for (int t = 0; t < n; t++) {
            int[] rows = rowsAt[t];
            coefXAt[t] = new double[rows.length];
            coefZAt[t] = new double[rows.length];
            for (int a = 0; a < rows.length; a++) {
                int j = rows[a];
                coefXAt[t][a] = coefX[j] != null ? coefX[j][t] : 0.0;
                coefZAt[t][a] = coefZ[j] != null ? coefZ[j][t] : 0.0;
            }
        }
        this.cone = new boolean[n];
        int cones = 0;
        for (int t = 0; t < n; t++) {
            cone[t] = mMag[t] > 0.0;
            if (cone[t]) cones++;
        }
        int boxPairs = 0;
        if (freeP0 != null) {
            boxLo[0] = freeP0.dvLoX;
            boxHi[0] = freeP0.dvHiX;
            boxLo[1] = freeP0.dvLoZ;
            boxHi[1] = freeP0.dvHiZ;
            for (int a = 0; a < 2; a++) {
                boxOn[a] = boxHi[a] > boxLo[a];
                if (boxOn[a]) boxPairs += 2;
            }
        }
        this.complementarityCount = cones + inequalities + boxPairs;
    }

    private int[][] rowsPerTick() {
        int[] count = new int[n];
        for (int j = 0; j < m; j++) {
            for (int t = 0; t <= lastCoupled[j]; t++) {
                if (couples(j, t)) count[t]++;
            }
        }
        int[][] out = new int[n][];
        for (int t = 0; t < n; t++) out[t] = new int[count[t]];
        int[] fill = new int[n];
        for (int j = 0; j < m; j++) {
            for (int t = 0; t <= lastCoupled[j]; t++) {
                if (couples(j, t)) out[t][fill[t]++] = j;
            }
        }
        return out;
    }

    private boolean couples(int j, int t) {
        double ax = coefX[j] != null ? coefX[j][t] : 0.0;
        double az = coefZ[j] != null ? coefZ[j][t] : 0.0;
        return !(ax == 0.0 && az == 0.0);
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
    }

    public static Result solve(int n, double[] cx, double[] cz, double[] mMag,
                               List<JumpLinearModel.Wall> walls) {
        return solve(n, cx, cz, mMag, walls, null);
    }

    public static Result solve(int n, double[] cx, double[] cz, double[] mMag,
                               List<JumpLinearModel.Wall> walls, CostateDualSolver.FreeP0 freeP0) {
        DiskSocpKernel k = new DiskSocpKernel(n, cx, cz, mMag, walls, freeP0, null);
        Result r = k.run();
        return k.failCode != FAIL_NONE ? null : r;
    }

    public static Outcome solveChords(int n, double[] cx, double[] cz, double[] mMag,
                                      List<JumpLinearModel.Wall> walls, CostateDualSolver.FreeP0 freeP0,
                                      List<ChordRow> chords) {
        return solveChords(n, cx, cz, mMag, walls, freeP0, chords, Double.NEGATIVE_INFINITY);
    }

    public static Outcome solveChords(int n, double[] cx, double[] cz, double[] mMag,
                                      List<JumpLinearModel.Wall> walls, CostateDualSolver.FreeP0 freeP0,
                                      List<ChordRow> chords, double cutoff) {
        DiskSocpKernel k = new DiskSocpKernel(n, cx, cz, mMag, walls, freeP0, chords);
        k.cutoff = cutoff;
        Result r = k.run();
        return new Outcome(r, k.failCode, k.failIter, k.failMu);
    }

    private static final class Step {
        final double[] lambda;
        final double[] slack;
        final double[] tau;
        final double[] gx;
        final double[] gz;
        final double[] x1;
        final double[] x2;
        final double[] omega = new double[2];
        final double[] v = new double[2];
        final double[] h = new double[2];

        Step(int n, int m) {
            lambda = new double[m];
            slack = new double[m];
            tau = new double[n];
            gx = new double[n];
            gz = new double[n];
            x1 = new double[n];
            x2 = new double[n];
        }
    }

    private Result run() {
        if (m == 0) return trivial();
        start();
        Step affine = new Step(n, m);
        Step step = new Step(n, m);
        double[] rhs = new double[m];
        double[] bestLambda = null;
        double[] bestX1 = null;
        double[] bestX2 = null;
        double[] bestV = null;
        double bestValue = Double.POSITIVE_INFINITY;
        double lowBound = primalLowBound();
        int nearOptimumCount = 0;
        int iters = 0;
        final long stepUnits = (long) m * m * (n + m) / 2L + 4L * m * n + 64L;
        for (int it = 0; it < MAX_ITERS; it++) {
            SolveClock.charge(SolveClock.KERNEL, stepUnits);
            measure();
            double mu = gap / complementarityCount;
            double value = boundValue(lambda, gx, gz);
            if (value < bestValue) {
                bestValue = value;
                bestLambda = lambda.clone();
                bestX1 = x1.clone();
                bestX2 = x2.clone();
                bestV = boxV.clone();
            }
            if (cutoff > Double.NEGATIVE_INFINITY && value <= cutoff) {
                fail(FAIL_CUTOFF, iters, mu);
                return emit(lambda, gx, gz, x1, x2, boxV, false, iters);
            }
            if (value < lowBound - UNBOUNDED_MARGIN * (1.0 + Math.abs(lowBound))) {
                fail(FAIL_UNBOUNDED, iters, mu);
                return emit(lambda, gx, gz, x1, x2, boxV, false, iters);
            }
            double valueScale = 1.0 + Math.abs(value);
            if (gap <= GAP_TOL * valueScale && residualMax <= RESIDUAL_TOL
                    && Math.abs(multiplierResidual) <= GAP_TOL * valueScale) {
                return emit(lambda, gx, gz, x1, x2, boxV, true, iters);
            }
            boolean nearOptimum = gap <= STALL_TOL * valueScale && residualMax <= STALL_TOL
                    && Math.abs(multiplierResidual) <= STALL_TOL * valueScale;
            if (nearOptimum && ++nearOptimumCount >= STALL_ITERS) {
                return emit(lambda, gx, gz, x1, x2, boxV, true, iters);
            }
            iters++;
            int broken = FAIL_NONE;
            if (!scaleCones()) {
                broken = FAIL_DET;
            } else {
                assembleSchur();
                if (!factor(schur, chol)) broken = FAIL_CHOLESKY;
            }
            double alphaPrimal = 0.0;
            double alphaDual = 0.0;
            if (broken == FAIL_NONE) {
                affineTargets();
                direction(affine, rhs);
                double alphaAffine = Math.min(1.0, maxStep(JOINT, affine));
                double ratio = Math.max(0.0, gapAfter(alphaAffine, affine)) / gap;
                double sigma = Math.min(1.0, ratio * ratio * ratio);
                centredTargets(sigma * mu, affine);
                direction(step, rhs);
                alphaPrimal = Math.min(1.0, STEP_FRACTION * maxStep(PRIMAL, step));
                alphaDual = Math.min(1.0, STEP_FRACTION * maxStep(DUAL, step));
                if (!(Math.max(alphaPrimal, alphaDual) > MIN_STEP)) broken = FAIL_STALLED;
            }
            if (broken != FAIL_NONE) {
                if (nearOptimum) return emit(lambda, gx, gz, x1, x2, boxV, true, iters);
                fail(broken, iters, mu);
                return emitBest(bestLambda, bestX1, bestX2, bestV, iters);
            }
            int moved = advance(alphaPrimal, alphaDual, step);
            if (moved == FAIL_NAN) {
                fail(FAIL_NAN, iters, mu);
                return emitBest(bestLambda, bestX1, bestX2, bestV, iters);
            }
            if (moved == FAIL_UNBOUNDED) {
                fail(FAIL_UNBOUNDED, iters, mu);
                return emit(lambda, gx, gz, x1, x2, boxV, false, iters);
            }
        }
        fail(FAIL_STALLED, iters, Double.NaN);
        return emitBest(bestLambda, bestX1, bestX2, bestV, iters);
    }

    private void start() {
        lambda = new double[m];
        slack = new double[m];
        tau = new double[n];
        gx = new double[n];
        gz = new double[n];
        x1 = new double[n];
        x2 = new double[n];
        for (int j = 0; j < m; j++) lambda[j] = eq[j] ? 0.0 : 1.0;
        costates(lambda, gx, gz);
        for (int t = 0; t < n; t++) {
            double norm = StrictMath.hypot(gx[t], gz[t]);
            tau[t] = cone[t] ? norm + 1.0 : norm;
        }
        if (freeP0 != null) {
            for (int a = 0; a < 2; a++) {
                boxV[a] = boxOn[a] ? 0.5 * (boxLo[a] + boxHi[a]) : boxLo[a];
                if (boxOn[a]) boxOmega[a] = Math.abs(hAxis(lambda, a)) + 1.0;
            }
        }
        for (int j = 0; j < m; j++) {
            if (eq[j]) continue;
            double s0 = bPrime[j];
            if (touchesBox(j)) s0 += p0coef[j] * boxV[axis[j]];
            slack[j] = Math.max(s0, 0.5);
        }
        residual = new double[m];
        h00 = new double[n];
        ratio1 = new double[n];
        ratio2 = new double[n];
        k11 = new double[n];
        k12 = new double[n];
        k22 = new double[n];
        beta = new double[n];
        vb0 = new double[n];
        vb1 = new double[n];
        vb2 = new double[n];
        nu0 = new double[n];
        nu1 = new double[n];
        nu2 = new double[n];
        schur = new double[m][m];
        chol = new double[m][m];
        q0 = new double[n];
        q1 = new double[n];
        q2 = new double[n];
        rowTarget = new double[m];
    }

    private boolean touchesBox(int j) {
        return freeP0 != null && (axis[j] == 0 || axis[j] == 1);
    }

    private void measure() {
        Arrays.fill(residual, 0.0);
        for (int t = 0; t < n; t++) {
            if (!cone[t]) continue;
            int[] rows = rowsAt[t];
            double[] rx = coefXAt[t];
            double[] rz = coefZAt[t];
            for (int a = 0; a < rows.length; a++) residual[rows[a]] += rx[a] * x1[t] + rz[a] * x2[t];
        }
        residualMax = 0.0;
        multiplierResidual = 0.0;
        for (int j = 0; j < m; j++) {
            double r = bPrime[j] + residual[j];
            if (touchesBox(j)) r += p0coef[j] * boxV[axis[j]];
            if (!eq[j]) r -= slack[j];
            residual[j] = r;
            residualMax = Math.max(residualMax, Math.abs(r));
            multiplierResidual += lambda[j] * r;
        }
        gap = 0.0;
        for (int t = 0; t < n; t++) if (cone[t]) gap += mMag[t] * tau[t] + x1[t] * gx[t] + x2[t] * gz[t];
        for (int j = 0; j < m; j++) if (!eq[j]) gap += slack[j] * lambda[j];
        for (int a = 0; a < 2; a++) {
            if (!boxOn[a]) continue;
            double h = hAxis(lambda, a);
            wPlus[a] = 0.5 * (boxOmega[a] + h);
            wMinus[a] = 0.5 * (boxOmega[a] - h);
            piPlus[a] = boxHi[a] - boxV[a];
            piMinus[a] = boxV[a] - boxLo[a];
            gap += piPlus[a] * wPlus[a] + piMinus[a] * wMinus[a];
        }
    }

    private boolean scaleCones() {
        for (int t = 0; t < n; t++) {
            if (!cone[t]) continue;
            double dx = coneDet(mMag[t], x1[t], x2[t]);
            double dz = coneDet(tau[t], gx[t], gz[t]);
            if (!(dx > 0.0) || !(dz > 0.0)) return false;
            double sx = StrictMath.sqrt(dx);
            double sz = StrictMath.sqrt(dz);
            double xh0 = mMag[t] / sx;
            double xh1 = x1[t] / sx;
            double xh2 = x2[t] / sx;
            double zh0 = tau[t] / sz;
            double zh1 = gx[t] / sz;
            double zh2 = gz[t] / sz;
            double gamma = StrictMath.sqrt(0.5 * (1.0 + xh0 * zh0 + xh1 * zh1 + xh2 * zh2));
            double w0 = (xh0 + zh0) / (2.0 * gamma);
            double w1 = (xh1 - zh1) / (2.0 * gamma);
            double w2 = (xh2 - zh2) / (2.0 * gamma);
            double b2 = sx / sz;
            double dd = 1.0 + 2.0 * (w1 * w1 + w2 * w2);
            h00[t] = b2 * dd;
            ratio1[t] = 2.0 * w0 * w1 / dd;
            ratio2[t] = 2.0 * w0 * w2 / dd;
            k11[t] = b2 * (1.0 + 2.0 * w2 * w2) / dd;
            k12[t] = -b2 * 2.0 * w1 * w2 / dd;
            k22[t] = b2 * (1.0 + 2.0 * w1 * w1) / dd;
            double den = StrictMath.sqrt(2.0 * (w0 + 1.0));
            beta[t] = StrictMath.sqrt(b2);
            vb0[t] = (w0 + 1.0) / den;
            vb1[t] = w1 / den;
            vb2[t] = w2 / den;
            double dot = vb0[t] * tau[t] + vb1[t] * gx[t] + vb2[t] * gz[t];
            nu0[t] = beta[t] * (2.0 * vb0[t] * dot - tau[t]);
            nu1[t] = beta[t] * (2.0 * vb1[t] * dot + gx[t]);
            nu2[t] = beta[t] * (2.0 * vb2[t] * dot + gz[t]);
        }
        return true;
    }

    private static double coneDet(double a0, double a1, double a2) {
        double norm = StrictMath.hypot(a1, a2);
        return (a0 - norm) * (a0 + norm);
    }

    private void assembleSchur() {
        for (int j = 0; j < m; j++) Arrays.fill(schur[j], 0.0);
        for (int t = 0; t < n; t++) {
            if (!cone[t]) continue;
            int[] rows = rowsAt[t];
            double[] rx = coefXAt[t];
            double[] rz = coefZAt[t];
            for (int a = 0; a < rows.length; a++) {
                double ka = k11[t] * rx[a] + k12[t] * rz[a];
                double kb = k12[t] * rx[a] + k22[t] * rz[a];
                double[] sj = schur[rows[a]];
                for (int b = 0; b <= a; b++) sj[rows[b]] += ka * rx[b] + kb * rz[b];
            }
        }
        for (int j = 0; j < m; j++) if (!eq[j]) schur[j][j] += slack[j] / lambda[j];
        for (int a = 0; a < 2; a++) {
            if (!boxOn[a]) continue;
            double d = wMinus[a] * piPlus[a] + wPlus[a] * piMinus[a];
            kappa[a] = piPlus[a] * piMinus[a] / d;
            for (int j = 0; j < m; j++) {
                if (axis[j] != a || p0coef[j] == 0.0) continue;
                for (int k = 0; k <= j; k++) {
                    if (axis[k] != a || p0coef[k] == 0.0) continue;
                    schur[j][k] += kappa[a] * p0coef[j] * p0coef[k];
                }
            }
        }
    }

    private void affineTargets() {
        for (int t = 0; t < n; t++) {
            if (!cone[t]) continue;
            q0[t] = -mMag[t];
            q1[t] = -x1[t];
            q2[t] = -x2[t];
        }
        for (int j = 0; j < m; j++) rowTarget[j] = eq[j] ? 0.0 : -slack[j];
        for (int a = 0; a < 2; a++) {
            if (!boxOn[a]) continue;
            targetPlus[a] = -piPlus[a] * wPlus[a];
            targetMinus[a] = -piMinus[a] * wMinus[a];
        }
    }

    private void centredTargets(double sigmaMu, Step affine) {
        for (int t = 0; t < n; t++) {
            if (!cone[t]) continue;
            double b = beta[t];
            double dotX = -vb1[t] * affine.x1[t] - vb2[t] * affine.x2[t];
            double a0 = (2.0 * vb0[t] * dotX) / b;
            double a1 = (2.0 * -vb1[t] * dotX + affine.x1[t]) / b;
            double a2 = (2.0 * -vb2[t] * dotX + affine.x2[t]) / b;
            double dotZ = vb0[t] * affine.tau[t] + vb1[t] * affine.gx[t] + vb2[t] * affine.gz[t];
            double c0 = b * (2.0 * vb0[t] * dotZ - affine.tau[t]);
            double c1 = b * (2.0 * vb1[t] * dotZ + affine.gx[t]);
            double c2 = b * (2.0 * vb2[t] * dotZ + affine.gz[t]);
            double r0 = sigmaMu - (nu0[t] * nu0[t] + nu1[t] * nu1[t] + nu2[t] * nu2[t]);
            double r1 = -2.0 * nu0[t] * nu1[t];
            double r2 = -2.0 * nu0[t] * nu2[t];
            r0 -= a0 * c0 + a1 * c1 + a2 * c2;
            r1 -= a0 * c1 + c0 * a1;
            r2 -= a0 * c2 + c0 * a2;
            double dn = coneDet(nu0[t], nu1[t], nu2[t]);
            double d0 = (nu0[t] * r0 - nu1[t] * r1 - nu2[t] * r2) / dn;
            double d1 = (r1 - d0 * nu1[t]) / nu0[t];
            double d2 = (r2 - d0 * nu2[t]) / nu0[t];
            double dot = vb0[t] * d0 + vb1[t] * d1 + vb2[t] * d2;
            q0[t] = b * (2.0 * vb0[t] * dot - d0);
            q1[t] = b * (2.0 * vb1[t] * dot + d1);
            q2[t] = b * (2.0 * vb2[t] * dot + d2);
        }
        for (int j = 0; j < m; j++) {
            if (eq[j]) {
                rowTarget[j] = 0.0;
                continue;
            }
            double corrector = affine.slack[j] * affine.lambda[j];
            rowTarget[j] = (sigmaMu - slack[j] * lambda[j] - corrector) / lambda[j];
        }
        for (int a = 0; a < 2; a++) {
            if (!boxOn[a]) continue;
            double correctorPlus = (-affine.v[a]) * 0.5 * (affine.omega[a] + affine.h[a]);
            double correctorMinus = (affine.v[a]) * 0.5 * (affine.omega[a] - affine.h[a]);
            targetPlus[a] = sigmaMu - piPlus[a] * wPlus[a] - correctorPlus;
            targetMinus[a] = sigmaMu - piMinus[a] * wMinus[a] - correctorMinus;
        }
    }

    private void direction(Step d, double[] rhs) {
        double vHatX = 0.0;
        double vHatZ = 0.0;
        for (int j = 0; j < m; j++) rhs[j] = -residual[j] + rowTarget[j];
        for (int t = 0; t < n; t++) {
            if (!cone[t]) continue;
            double qh1 = q1[t] - ratio1[t] * q0[t];
            double qh2 = q2[t] - ratio2[t] * q0[t];
            int[] rows = rowsAt[t];
            double[] rx = coefXAt[t];
            double[] rz = coefZAt[t];
            for (int a = 0; a < rows.length; a++) rhs[rows[a]] -= rx[a] * qh1 + rz[a] * qh2;
        }
        for (int a = 0; a < 2; a++) {
            if (!boxOn[a]) continue;
            double den = wMinus[a] * piPlus[a] + wPlus[a] * piMinus[a];
            double vHat = (piPlus[a] * targetMinus[a] - piMinus[a] * targetPlus[a]) / den;
            if (a == 0) vHatX = vHat;
            else vHatZ = vHat;
            for (int j = 0; j < m; j++) if (axis[j] == a) rhs[j] -= p0coef[j] * vHat;
        }
        System.arraycopy(rhs, 0, d.lambda, 0, m);
        solveFactored(chol, d.lambda);
        for (int j = 0; j < m; j++) {
            d.slack[j] = eq[j] ? 0.0 : rowTarget[j] - slack[j] / lambda[j] * d.lambda[j];
        }
        for (int t = 0; t < n; t++) {
            int[] rows = rowsAt[t];
            double[] rx = coefXAt[t];
            double[] rz = coefZAt[t];
            double g0 = 0.0;
            double g1 = 0.0;
            for (int a = 0; a < rows.length; a++) {
                g0 -= rx[a] * d.lambda[rows[a]];
                g1 -= rz[a] * d.lambda[rows[a]];
            }
            d.gx[t] = g0;
            d.gz[t] = g1;
            if (!cone[t]) {
                d.tau[t] = 0.0;
                d.x1[t] = 0.0;
                d.x2[t] = 0.0;
                continue;
            }
            d.tau[t] = q0[t] / h00[t] - ratio1[t] * g0 - ratio2[t] * g1;
            d.x1[t] = q1[t] - ratio1[t] * q0[t] - k11[t] * g0 - k12[t] * g1;
            d.x2[t] = q2[t] - ratio2[t] * q0[t] - k12[t] * g0 - k22[t] * g1;
        }
        for (int a = 0; a < 2; a++) {
            if (!boxOn[a]) {
                d.omega[a] = 0.0;
                d.v[a] = 0.0;
                d.h[a] = 0.0;
                continue;
            }
            double h = 0.0;
            for (int j = 0; j < m; j++) if (axis[j] == a) h += p0coef[j] * d.lambda[j];
            d.h[a] = h;
            double den = wMinus[a] * piPlus[a] + wPlus[a] * piMinus[a];
            d.v[a] = (a == 0 ? vHatX : vHatZ) + kappa[a] * h;
            d.omega[a] = (2.0 * (wMinus[a] * targetPlus[a] + wPlus[a] * targetMinus[a])
                    - (wMinus[a] * piPlus[a] - wPlus[a] * piMinus[a]) * h) / den;
        }
    }

    private double maxStep(int side, Step d) {
        double amax = INF;
        for (int j = 0; j < m; j++) {
            if (eq[j]) continue;
            if (side != PRIMAL && d.lambda[j] < 0.0) amax = Math.min(amax, -lambda[j] / d.lambda[j]);
            if (side != DUAL && d.slack[j] < 0.0) amax = Math.min(amax, -slack[j] / d.slack[j]);
        }
        for (int t = 0; t < n; t++) {
            if (!cone[t]) continue;
            if (side != PRIMAL) {
                amax = Math.min(amax, maxStepSoc(tau[t], gx[t], gz[t], d.tau[t], d.gx[t], d.gz[t]));
            }
            if (side != DUAL) {
                amax = Math.min(amax, maxStepSoc(mMag[t], x1[t], x2[t], 0.0, d.x1[t], d.x2[t]));
            }
        }
        for (int a = 0; a < 2; a++) {
            if (!boxOn[a]) continue;
            double dwp = 0.5 * (d.omega[a] + d.h[a]);
            double dwm = 0.5 * (d.omega[a] - d.h[a]);
            if (side != PRIMAL && dwp < 0.0) amax = Math.min(amax, -wPlus[a] / dwp);
            if (side != PRIMAL && dwm < 0.0) amax = Math.min(amax, -wMinus[a] / dwm);
            if (side != DUAL && d.v[a] > 0.0) amax = Math.min(amax, piPlus[a] / d.v[a]);
            if (side != DUAL && d.v[a] < 0.0) amax = Math.min(amax, -piMinus[a] / d.v[a]);
        }
        return amax;
    }

    private double gapAfter(double alpha, Step d) {
        double after = 0.0;
        for (int t = 0; t < n; t++) {
            if (!cone[t]) continue;
            after += mMag[t] * (tau[t] + alpha * d.tau[t])
                    + (x1[t] + alpha * d.x1[t]) * (gx[t] + alpha * d.gx[t])
                    + (x2[t] + alpha * d.x2[t]) * (gz[t] + alpha * d.gz[t]);
        }
        for (int j = 0; j < m; j++) {
            if (!eq[j]) after += (slack[j] + alpha * d.slack[j]) * (lambda[j] + alpha * d.lambda[j]);
        }
        for (int a = 0; a < 2; a++) {
            if (!boxOn[a]) continue;
            after += (piPlus[a] - alpha * d.v[a]) * (wPlus[a] + alpha * 0.5 * (d.omega[a] + d.h[a]))
                    + (piMinus[a] + alpha * d.v[a]) * (wMinus[a] + alpha * 0.5 * (d.omega[a] - d.h[a]));
        }
        return after;
    }

    private int advance(double alphaPrimal, double alphaDual, Step d) {
        boolean capped = false;
        for (int j = 0; j < m; j++) {
            lambda[j] += alphaDual * d.lambda[j];
            if (!eq[j]) slack[j] += alphaPrimal * d.slack[j];
            if (Double.isNaN(lambda[j])) return FAIL_NAN;
            if (Math.abs(lambda[j]) > LAMBDA_CAP) capped = true;
        }
        costates(lambda, gx, gz);
        for (int t = 0; t < n; t++) {
            double norm = StrictMath.hypot(gx[t], gz[t]);
            if (!cone[t]) {
                tau[t] = norm;
                continue;
            }
            tau[t] += alphaDual * d.tau[t];
            x1[t] += alphaPrimal * d.x1[t];
            x2[t] += alphaPrimal * d.x2[t];
            if (!(tau[t] > norm)) tau[t] = norm * (1.0 + 1.0e-12) + 1.0e-300;
        }
        for (int a = 0; a < 2; a++) {
            if (!boxOn[a]) continue;
            boxV[a] += alphaPrimal * d.v[a];
            boxOmega[a] += alphaDual * d.omega[a];
            double h = Math.abs(hAxis(lambda, a));
            if (!(boxOmega[a] > h)) boxOmega[a] = h * (1.0 + 1.0e-12) + 1.0e-300;
        }
        return capped ? FAIL_UNBOUNDED : FAIL_NONE;
    }

    private Result emitBest(double[] bestLambda, double[] bestX1, double[] bestX2, double[] bestV, int iters) {
        if (bestLambda == null) return null;
        double[] bestGx = new double[n];
        double[] bestGz = new double[n];
        costates(bestLambda, bestGx, bestGz);
        return emit(bestLambda, bestGx, bestGz, bestX1, bestX2, bestV, false, iters);
    }

    private Result emit(double[] lambda, double[] gx, double[] gz, double[] x1, double[] x2, double[] v,
                        boolean converged, int iters) {
        double[] ux = new double[n];
        double[] uz = new double[n];
        for (int t = 0; t < n; t++) {
            ux[t] = -x1[t];
            uz[t] = -x2[t];
        }
        double[] lambdaOut = new double[m];
        for (int j = 0; j < m; j++) lambdaOut[j] = lambda[j] / scale[j];
        return new Result(gx.clone(), gz.clone(), ux, uz, lambdaOut, boundValue(lambda, gx, gz), converged, iters,
                gap, freeP0 != null ? v[0] : 0.0, freeP0 != null ? v[1] : 0.0);
    }

    private double boundValue(double[] lambda, double[] gx, double[] gz) {
        double value = 0.0;
        for (int t = 0; t < n; t++) value += mMag[t] * StrictMath.hypot(gx[t], gz[t]);
        for (int j = 0; j < m; j++) value += lambda[j] * bPrime[j];
        if (freeP0 != null) {
            double hx = hAxis(lambda, 0);
            double hz = hAxis(lambda, 1);
            value += hx >= 0.0 ? hx * freeP0.dvHiX : hx * freeP0.dvLoX;
            value += hz >= 0.0 ? hz * freeP0.dvHiZ : hz * freeP0.dvLoZ;
        }
        return value;
    }

    private double primalLowBound() {
        double lb = 0.0;
        for (int t = 0; t < n; t++) lb -= mMag[t] * StrictMath.hypot(cx[t], cz[t]);
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

    private Result trivial() {
        double[] gx = cx.clone();
        double[] gz = cz.clone();
        double[] ux = new double[n];
        double[] uz = new double[n];
        double value = 0.0;
        for (int t = 0; t < n; t++) {
            double nrm = StrictMath.hypot(gx[t], gz[t]);
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
            value += freeP0.support(hx, 0) + freeP0.support(hz, 1);
            dvx = freeP0.delta(hx, 0);
            dvz = freeP0.delta(hz, 1);
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

    private static double maxStepSoc(double v0, double v1, double v2, double d0, double d1, double d2) {
        double a = d0 * d0 - d1 * d1 - d2 * d2;
        double b = v0 * d0 - v1 * d1 - v2 * d2;
        double c = v0 * v0 - v1 * v1 - v2 * v2;
        double step = INF;
        if (a < 0.0) {
            double disc = b * b - a * c;
            double sq = StrictMath.sqrt(disc);
            step = Math.min(step, minPos((-b - sq) / a, (-b + sq) / a));
        } else if (a > 0.0) {
            double disc = b * b - a * c;
            if (disc > 0.0) {
                double sq = StrictMath.sqrt(disc);
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
            if (SpdCholesky.factor(S, L, m, jitter)) return true;
            jitter = jitter == 0.0 ? 1.0e-12 * (maxDiag + 1.0) : jitter * 10.0;
        }
        if (DEBUG) {
            double probe = jitter;
            int extra = 0;
            while (extra < 20 && !SpdCholesky.factor(S, L, m, probe)) {
                probe *= 10.0;
                extra++;
            }
            System.out.printf("  factor FAIL m=%d minDiag=%.3e maxDiag=%.3e lastJitter=%.3e passJitter=%.3e%n",
                    m, minDiag, maxDiag, jitter, extra < 20 ? probe : Double.NaN);
        }
        return false;
    }

    private void solveFactored(double[][] L, double[] x) {
        SpdCholesky.solveInPlace(L, x, m);
    }
}
