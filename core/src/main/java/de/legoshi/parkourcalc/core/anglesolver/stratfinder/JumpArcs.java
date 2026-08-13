package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

public final class JumpArcs {

    public static final double JUMP_V0 = 0.42;
    public static final double GRAVITY = 0.08;
    public static final double DRAG = 0.98;
    public static final double PLAYER_HEIGHT = 1.8;
    public static final int MAX_TICKS = 200;

    private JumpArcs() {
    }

    public static boolean legacyThreshold(String mcVersion) {
        return mcVersion != null && (mcVersion.startsWith("1.8") || mcVersion.startsWith("1.12"));
    }

    public static int duration(double deltaY, boolean legacy) {
        return duration(deltaY, Double.NaN, legacy);
    }

    public static int duration(double deltaY, double headroom, boolean legacy) {
        double threshold = legacy ? 0.005 : 0.003;
        double cap = Double.isNaN(headroom) ? Double.POSITIVE_INFINITY : headroom - PLAYER_HEIGHT;
        double y = 0.0;
        double vy = JUMP_V0;
        double prev = 0.0;
        for (int t = 1; t <= MAX_TICKS; t++) {
            y += vy;
            if (y > cap) {
                y = cap;
                vy = 0.0;
            }
            if (y <= deltaY && prev > deltaY) {
                return t;
            }
            prev = y;
            vy = (vy - GRAVITY) * DRAG;
            if (Math.abs(vy) < threshold) {
                vy = 0.0;
            }
        }
        return -1;
    }

    public static double[] heights(int duration, double deltaY, boolean legacy) {
        return heights(duration, deltaY, Double.NaN, legacy);
    }

    public static double[] heights(int duration, double deltaY, double headroom, boolean legacy) {
        double threshold = legacy ? 0.005 : 0.003;
        double cap = Double.isNaN(headroom) ? Double.POSITIVE_INFINITY : headroom - PLAYER_HEIGHT;
        double[] out = new double[duration + 1];
        double y = 0.0;
        double vy = JUMP_V0;
        for (int t = 1; t <= duration; t++) {
            y += vy;
            if (y > cap) {
                y = cap;
                vy = 0.0;
            }
            out[t] = t == duration ? deltaY : y;
            vy = (vy - GRAVITY) * DRAG;
            if (Math.abs(vy) < threshold) {
                vy = 0.0;
            }
        }
        return out;
    }

    public static double maxRise(boolean legacy) {
        double threshold = legacy ? 0.005 : 0.003;
        double y = 0.0;
        double vy = JUMP_V0;
        double max = 0.0;
        for (int t = 1; t <= MAX_TICKS; t++) {
            y += vy;
            max = Math.max(max, y);
            vy = (vy - GRAVITY) * DRAG;
            if (Math.abs(vy) < threshold) {
                vy = 0.0;
            }
        }
        return max;
    }
}
