package de.legoshi.parkourcalc.core.anglesolver.solver;

public final class HeadingBand {

    public static final double MIN_SPEED = 0.05;
    public static final double FULL_SPEED = 0.15;
    public static final int LOOK_BACK_TICKS = 2;
    public static final int LOOK_AHEAD_TICKS = 8;
    public static final double DEADBAND_DEG = 50.0;

    private final int n;
    private final double[] heading;
    private final boolean[] moving;
    private final double[] lo;
    private final double[] hi;
    private final double[] weight;
    private final double headingTravelDeg;

    private HeadingBand(int n, double[] heading, boolean[] moving, double[] lo, double[] hi, double[] weight,
                        double headingTravelDeg) {
        this.n = n;
        this.heading = heading;
        this.moving = moving;
        this.lo = lo;
        this.hi = hi;
        this.weight = weight;
        this.headingTravelDeg = headingTravelDeg;
    }

    public static HeadingBand of(ForwardPath path, double[] yawUnwrapped) {
        return of(path, yawUnwrapped, LOOK_BACK_TICKS, LOOK_AHEAD_TICKS, DEADBAND_DEG);
    }

    public static HeadingBand of(ForwardPath path, double[] yawUnwrapped, int back, int ahead, double deadband) {
        int n = yawUnwrapped.length;
        double[] heading = new double[n];
        double[] speed = new double[n];
        boolean[] moving = new boolean[n];
        for (int t = 0; t < n; t++) {
            double dx = path.posX[t + 1] - path.posX[t];
            double dz = path.posZ[t + 1] - path.posZ[t];
            speed[t] = StrictMath.hypot(dx, dz);
            if (speed[t] > MIN_SPEED) {
                heading[t] = Angles.deg(StrictMath.atan2(-dx, dz));
                moving[t] = true;
            }
        }
        double travel = 0.0;
        double prev = Double.NaN;
        for (int t = 0; t < n; t++) {
            if (!moving[t]) {
                continue;
            }
            if (!Double.isNaN(prev)) {
                travel += Math.abs(Angles.wrapDelta(heading[t] - prev));
            }
            prev = heading[t];
        }
        double[] lo = new double[n];
        double[] hi = new double[n];
        double[] weight = new double[n];
        for (int t = 0; t < n; t++) {
            lo[t] = Double.NEGATIVE_INFINITY;
            hi[t] = Double.POSITIVE_INFINITY;
            int centre = nearestMoving(moving, t, back, ahead, n);
            if (centre < 0) {
                continue;
            }
            double start = yawUnwrapped[t] + Angles.wrapDelta(heading[centre] - yawUnwrapped[t]);
            double min = start;
            double max = start;
            double cur = start;
            for (int k = centre + 1; k <= Math.min(n - 1, t + ahead); k++) {
                if (!moving[k]) {
                    continue;
                }
                cur += Angles.wrapDelta(heading[k] - cur);
                min = Math.min(min, cur);
                max = Math.max(max, cur);
            }
            cur = start;
            for (int k = centre - 1; k >= Math.max(0, t - back); k--) {
                if (!moving[k]) {
                    continue;
                }
                cur += Angles.wrapDelta(heading[k] - cur);
                min = Math.min(min, cur);
                max = Math.max(max, cur);
            }
            if (max - min + 2.0 * deadband >= 360.0) {
                continue;
            }
            double mid = 0.5 * (min + max);
            double shift = 360.0 * Math.rint((yawUnwrapped[t] - mid) / 360.0);
            lo[t] = min - deadband + shift;
            hi[t] = max + deadband + shift;
            double own = moving[t] ? speed[t] : 0.0;
            weight[t] = Math.max(0.25, Math.min(1.0, own / FULL_SPEED));
        }
        return new HeadingBand(n, heading, moving, lo, hi, weight, travel);
    }

    private static int nearestMoving(boolean[] moving, int t, int back, int ahead, int n) {
        for (int k = 0; k <= Math.max(back, ahead); k++) {
            if (k <= ahead && t + k < n && moving[t + k]) {
                return t + k;
            }
            if (k <= back && t - k >= 0 && moving[t - k]) {
                return t - k;
            }
        }
        return -1;
    }

    public double excess(int tick, double yawUnwrapped) {
        if (yawUnwrapped < lo[tick]) {
            return yawUnwrapped - lo[tick];
        }
        if (yawUnwrapped > hi[tick]) {
            return yawUnwrapped - hi[tick];
        }
        return 0.0;
    }

    public double weight(int tick) {
        return weight[tick];
    }

    public double offHeadingDeg(double[] yawUnwrapped) {
        double sum = 0.0;
        for (int t = 0; t < n; t++) {
            sum += Math.abs(excess(t, yawUnwrapped[t]));
        }
        return sum;
    }

    public double headingTravelDeg() {
        return headingTravelDeg;
    }

    public boolean movingAt(int tick) {
        return moving[tick];
    }

    public double headingAt(int tick) {
        return heading[tick];
    }
}
