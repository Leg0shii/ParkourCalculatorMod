package de.legoshi.parkourcalc.core.anglesolver.solver;

public final class PathTranslation {

    private PathTranslation() {
    }

    static final int AXIS_TERNARY_ITERS = 60;

    public static final class Trans {
        public final double tx;
        public final double tz;
        public final double viol;

        public Trans(double tx, double tz, double viol) {
            this.tx = tx;
            this.tz = tz;
            this.viol = viol;
        }
    }

    static final int PICK_MID = 0;
    static final int PICK_LO = 1;
    static final int PICK_HI = 2;
    static final double OBJ_BACKOFF = 1.0e-9;

    public static Trans bestTranslation(JumpConstraintCompiler.Compiled compiled, double[] gf, ForwardPath path,
                                        double loX, double hiX, double loZ, double hiZ) {
        return translationCore(compiled, gf, path, loX, hiX, loZ, hiZ, -1, false);
    }

    public static Trans bestTranslationObj(JumpConstraintCompiler.Compiled compiled, double[] gf, ForwardPath path,
                                           double loX, double hiX, double loZ, double hiZ,
                                           int objAxisIdx, boolean objMax) {
        return translationCore(compiled, gf, path, loX, hiX, loZ, hiZ, objAxisIdx, objMax);
    }

    private static Trans translationCore(JumpConstraintCompiler.Compiled compiled, double[] gf, ForwardPath path,
                                         double loX, double hiX, double loZ, double hiZ,
                                         int objAxisIdx, boolean objMax) {
        int ni = compiled.ineq.size();
        int ne = compiled.eq.size();
        int cap = Math.max(ni + 2 * ne, 1);
        double[] ax = new double[cap];
        double[] bx = new double[cap];
        double[] az = new double[cap];
        double[] bz = new double[cap];
        int nx = 0;
        int nz = 0;
        double floor = 0.0;
        for (JumpConstraint c : compiled.ineq) {
            int axis = c.mode == JumpConstraint.Mode.X ? 0 : c.mode == JumpConstraint.Mode.Z ? 1 : -1;
            if (axis < 0) {
                double s = JumpConstraintCompiler.slack(c, gf, path);
                if (s > floor) floor = s;
                continue;
            }
            double e0 = JumpConstraintCompiler.evaluate(c, gf, path);
            int tc = (c.t2 == null) ? 1 : (c.op == JumpConstraint.Op.PLUS ? 2 : 0);
            double g0 = c.cmp == JumpConstraint.Cmp.GE ? -e0 : e0;
            double beta = c.cmp == JumpConstraint.Cmp.GE ? -tc : tc;
            if (axis == 0) {
                ax[nx] = g0;
                bx[nx] = beta;
                nx++;
            } else {
                az[nz] = g0;
                bz[nz] = beta;
                nz++;
            }
        }
        for (JumpConstraint c : compiled.eq) {
            int axis = c.mode == JumpConstraint.Mode.X ? 0 : c.mode == JumpConstraint.Mode.Z ? 1 : -1;
            if (axis < 0) {
                double a = Math.abs(JumpConstraintCompiler.evaluate(c, gf, path));
                if (a > floor) floor = a;
                continue;
            }
            double e0 = JumpConstraintCompiler.evaluate(c, gf, path);
            int tc = (c.t2 == null) ? 1 : (c.op == JumpConstraint.Op.PLUS ? 2 : 0);
            if (axis == 0) {
                ax[nx] = e0;
                bx[nx] = tc;
                nx++;
                ax[nx] = -e0;
                bx[nx] = -tc;
                nx++;
            } else {
                az[nz] = e0;
                bz[nz] = tc;
                nz++;
                az[nz] = -e0;
                bz[nz] = -tc;
                nz++;
            }
        }
        double[] ox = new double[2];
        double[] oz = new double[2];
        solveAxis(ax, bx, nx, loX, hiX, pickFor(0, objAxisIdx, objMax), ox);
        solveAxis(az, bz, nz, loZ, hiZ, pickFor(1, objAxisIdx, objMax), oz);
        double viol = Math.max(Math.max(ox[1], oz[1]), floor);
        return new Trans(ox[0], oz[0], viol);
    }

    private static int pickFor(int axis, int objAxisIdx, boolean objMax) {
        if (axis != objAxisIdx) return PICK_MID;
        return objMax ? PICK_HI : PICK_LO;
    }

    static void solveAxis(double[] a, double[] b, int cnt, double lo, double hi, int pick, double[] out) {
        double lower = lo;
        double upper = hi;
        for (int i = 0; i < cnt; i++) {
            double bi = b[i];
            double ai = a[i];
            if (bi > 0.0) {
                double u = -ai / bi;
                if (u < upper) upper = u;
            } else if (bi < 0.0) {
                double l = -ai / bi;
                if (l > lower) lower = l;
            }
        }
        if (lower <= upper) {
            double d;
            if (pick == PICK_HI) {
                d = Math.max(lower, Math.min(upper, upper - OBJ_BACKOFF));
            } else if (pick == PICK_LO) {
                d = Math.min(upper, Math.max(lower, lower + OBJ_BACKOFF));
            } else {
                d = 0.5 * (lower + upper);
            }
            out[0] = d;
            out[1] = axisF(a, b, cnt, d);
            return;
        }
        double clo = lo;
        double chi = hi;
        for (int it = 0; it < AXIS_TERNARY_ITERS; it++) {
            double m1 = clo + (chi - clo) / 3.0;
            double m2 = chi - (chi - clo) / 3.0;
            if (axisF(a, b, cnt, m1) < axisF(a, b, cnt, m2)) chi = m2;
            else clo = m1;
        }
        double d = 0.5 * (clo + chi);
        out[0] = d;
        out[1] = axisF(a, b, cnt, d);
    }

    static double axisF(double[] a, double[] b, int cnt, double d) {
        double v = 0.0;
        for (int i = 0; i < cnt; i++) {
            double val = a[i] + b[i] * d;
            if (val > v) v = val;
        }
        return v;
    }

}
