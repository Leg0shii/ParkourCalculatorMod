package de.legoshi.parkourcalc.core.anglesolver.solver;

public final class SupportOverlap {

    private static final float HALF_WIDTH = 0.6F / 2.0F;
    private static final double COLLIDE_EPSILON = 1.0E-7;

    private SupportOverlap() {
    }

    public static boolean supports(boolean modernCollision, double center, double supportLo, double supportHi) {
        double boxMax = center + HALF_WIDTH;
        double boxMin = center - HALF_WIDTH;
        if (modernCollision) {
            return boxMax - COLLIDE_EPSILON >= supportLo && boxMin + COLLIDE_EPSILON < supportHi;
        }
        return boxMax > supportLo && boxMin < supportHi;
    }

    public static double minCenter(boolean modernCollision, double supportLo, double supportHi) {
        double c = modernCollision ? (supportLo + COLLIDE_EPSILON) - HALF_WIDTH : supportLo - HALF_WIDTH;
        while (!supports(modernCollision, c, supportLo, supportHi)) {
            c = Math.nextUp(c);
        }
        while (supports(modernCollision, Math.nextDown(c), supportLo, supportHi)) {
            c = Math.nextDown(c);
        }
        return c;
    }

    public static double maxCenter(boolean modernCollision, double supportLo, double supportHi) {
        double c = modernCollision ? (supportHi - COLLIDE_EPSILON) + HALF_WIDTH : supportHi + HALF_WIDTH;
        while (!supports(modernCollision, c, supportLo, supportHi)) {
            c = Math.nextDown(c);
        }
        while (supports(modernCollision, Math.nextUp(c), supportLo, supportHi)) {
            c = Math.nextUp(c);
        }
        return c;
    }
}
