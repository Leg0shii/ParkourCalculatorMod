package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.Locale;

public final class TurnShape {

    public static final double FLOOR_DEG = 0.01;
    public static final double ACCEL_FLOOR_DEG = 0.05;
    private static final double TOL = 1.0e-9;

    public final int reversals;
    public final int accelFlips;
    public final double snapDeg;
    public final double jerkDeg;

    private TurnShape(int reversals, int accelFlips, double snapDeg, double jerkDeg) {
        this.reversals = reversals;
        this.accelFlips = accelFlips;
        this.snapDeg = snapDeg;
        this.jerkDeg = jerkDeg;
    }

    public static double[] deltas(double anchorYaw, double[] absWrapped) {
        double[] d = new double[absWrapped.length];
        double prev = anchorYaw;
        for (int i = 0; i < absWrapped.length; i++) {
            d[i] = Angles.wrapDelta(absWrapped[i] - prev);
            prev = absWrapped[i];
        }
        return d;
    }

    public static TurnShape of(double anchorYaw, double[] absWrapped) {
        return ofDeltas(deltas(anchorYaw, absWrapped));
    }

    public static TurnShape ofDeltas(double[] d) {
        int reversals = 0;
        int flips = 0;
        int runSign = 0;
        int accelSign = 0;
        double prevMag = 0.0;
        boolean prevInRun = false;
        for (double v : d) {
            if (Math.abs(v) <= FLOOR_DEG) {
                prevInRun = false;
                continue;
            }
            int sign = v > 0.0 ? 1 : -1;
            if (runSign != 0 && sign != runSign) {
                reversals++;
                prevInRun = false;
            }
            if (prevInRun) {
                double a = Math.abs(v) - prevMag;
                if (Math.abs(a) > ACCEL_FLOOR_DEG) {
                    int as = a > 0.0 ? 1 : -1;
                    if (accelSign != 0 && as != accelSign) flips++;
                    accelSign = as;
                }
            } else {
                accelSign = 0;
            }
            runSign = sign;
            prevMag = Math.abs(v);
            prevInRun = true;
        }
        double jerk = 0.0;
        double snap = 0.0;
        for (int i = 1; i < d.length; i++) {
            double a = d[i] - d[i - 1];
            jerk += Math.abs(a);
            if (i >= 2) snap += Math.abs(a - (d[i - 1] - d[i - 2]));
        }
        return new TurnShape(reversals, flips, snap, jerk);
    }

    public boolean betterThan(TurnShape o) {
        if (reversals != o.reversals) return reversals < o.reversals;
        if (accelFlips != o.accelFlips) return accelFlips < o.accelFlips;
        if (Math.abs(snapDeg - o.snapDeg) > TOL) return snapDeg < o.snapDeg;
        return jerkDeg < o.jerkDeg - TOL;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "rev=%d flips=%d snap=%.2f jerk=%.2f", reversals, accelFlips, snapDeg, jerkDeg);
    }
}
