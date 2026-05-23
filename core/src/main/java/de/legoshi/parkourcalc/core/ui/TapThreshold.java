package de.legoshi.parkourcalc.core.ui;

public final class TapThreshold {

    public static final double ENGAGE_PX = 2.0;

    public static boolean exceeded(double pressX, double pressY, double curX, double curY) {
        double dx = curX - pressX;
        double dy = curY - pressY;
        return Math.hypot(dx, dy) >= ENGAGE_PX;
    }

    private TapThreshold() {}
}
