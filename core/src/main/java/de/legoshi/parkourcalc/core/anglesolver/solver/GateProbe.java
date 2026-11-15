package de.legoshi.parkourcalc.core.anglesolver.solver;

public final class GateProbe {

    public interface ImpulseReach {
        double of(int tick, double ax, double az);
    }

    private static final int GROWTH_STEPS = 14;
    private static final int SECTION_STEPS = 24;
    private static final double GOLDEN = 0.6180339887498949;
    private static final double SECTION_REL = 1.0e-6;

    private final SineTableGeometry geom;
    private final ImpulseReach reach;
    private final int n;
    private final double[] loDeg;
    private final double[] hiDeg;
    private final double[] gx;
    private final double[] gz;
    private final double[] tx;
    private final double[] tz;
    private final double[] support;
    private final double dualValue;
    private final double target;
    private final double firstStep;

    public GateProbe(SineTableGeometry geom, ImpulseReach reach, double[] loDeg, double[] hiDeg, double[] gx, double[] gz,
              double[] tx, double[] tz, double[] support, double dualValue, double target, double firstStep) {
        this.geom = geom;
        this.reach = reach;
        this.n = gx.length;
        this.loDeg = loDeg;
        this.hiDeg = hiDeg;
        this.gx = gx;
        this.gz = gz;
        this.tx = tx;
        this.tz = tz;
        this.support = support;
        this.dualValue = dualValue;
        this.target = target;
        this.firstStep = firstStep;
    }

    public double valueWith(JumpLinearModel.Wall row, double multiplier) {
        double v = dualValue + multiplier * row.bPrime;
        double[] ax = row.coefX;
        double[] az = row.coefZ;
        for (int t = 0; t < n; t++) {
            double dx = ax != null && t < ax.length ? multiplier * ax[t] : 0.0;
            double dz = az != null && t < az.length ? multiplier * az[t] : 0.0;
            if (dx == 0.0 && dz == 0.0) continue;
            v += geom.support(t, gx[t] - dx, gz[t] - dz, loDeg[t], hiDeg[t]) - support[t];
            v += reach.of(t, tx[t] - dx, tz[t] - dz) - reach.of(t, tx[t], tz[t]);
        }
        return v;
    }

    public boolean excludes(JumpLinearModel.Wall row) {
        if (row == null) return false;
        double a = 0.0;
        double fa = dualValue;
        double m = firstStep;
        double fm = valueWith(row, m);
        if (fm <= target) return true;
        double b = m;
        double fb = fm;
        if (fm < fa) {
            b = 2.0 * m;
            fb = valueWith(row, b);
            if (fb <= target) return true;
            int grown = 0;
            while (fb < fm) {
                if (++grown > GROWTH_STEPS) return false;
                a = m;
                fa = fm;
                m = b;
                fm = fb;
                b = 2.0 * b;
                fb = valueWith(row, b);
                if (fb <= target) return true;
            }
        }
        double c = b - GOLDEN * (b - a);
        double d = a + GOLDEN * (b - a);
        double fc = valueWith(row, c);
        if (fc <= target) return true;
        double fd = valueWith(row, d);
        if (fd <= target) return true;
        for (int it = 0; it < SECTION_STEPS; it++) {
            if (convexFloor(a, fa, c, fc, d, fd, b, fb) > target) return false;
            if (b - a < SECTION_REL * (1.0 + b)) return false;
            if (fc < fd) {
                b = d;
                fb = fd;
                d = c;
                fd = fc;
                c = b - GOLDEN * (b - a);
                fc = valueWith(row, c);
                if (fc <= target) return true;
            } else {
                a = c;
                fa = fc;
                c = d;
                fc = fd;
                d = a + GOLDEN * (b - a);
                fd = valueWith(row, d);
                if (fd <= target) return true;
            }
        }
        return false;
    }

    public static double convexFloor(double x0, double f0, double x1, double f1, double x2, double f2, double x3,
                              double f3) {
        double s01 = (f1 - f0) / (x1 - x0);
        double s12 = (f2 - f1) / (x2 - x1);
        double s23 = (f3 - f2) / (x3 - x2);
        double left = Math.min(f1, f1 - s12 * (x1 - x0));
        double right = Math.min(f2, f2 + s12 * (x3 - x2));
        double fromLeft = Math.min(f1, f1 + s01 * (x2 - x1));
        double fromRight = Math.min(f2, f2 - s23 * (x2 - x1));
        double middle = Math.max(fromLeft, fromRight);
        return Math.min(left, Math.min(middle, right));
    }
}
