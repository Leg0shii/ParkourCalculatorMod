package de.legoshi.parkourcalc.core.anglesolver.solver;

public final class FacingReconstruction {

    private FacingReconstruction() {
    }

    public static final class Result {
        public final double[] absYaws;
        public final boolean ok;
        public final int failedTick;
        public final int maxUlpNudge;

        Result(double[] absYaws, boolean ok, int failedTick, int maxUlpNudge) {
            this.absYaws = absYaws;
            this.ok = ok;
            this.failedTick = failedTick;
            this.maxUlpNudge = maxUlpNudge;
        }
    }

    public static Result reconstruct(double[] gameFacings, JumpPhysicsInputs scenario) {
        int n = gameFacings.length;
        double[] abs = new double[n];
        double prevAbs = (double) scenario.startYaw;
        float prevFacing = scenario.startYaw;
        int maxNudge = 0;
        int[] nudgeOut = new int[1];
        for (int k = 0; k < n; k++) {
            boolean locked = scenario.yawLockedPerTick != null
                    && k < scenario.yawLockedPerTick.length
                    && scenario.yawLockedPerTick[k];
            float target = (float) gameFacings[k];
            if (locked) {
                abs[k] = (double) target;
            } else {
                Float d = findDelta(prevFacing, target, nudgeOut);
                if (d == null) {
                    return new Result(abs, false, k, maxNudge);
                }
                if (nudgeOut[0] > maxNudge) {
                    maxNudge = nudgeOut[0];
                }
                abs[k] = prevAbs + (double) d.floatValue();
            }
            prevAbs = abs[k];
            prevFacing = target;
        }
        return new Result(abs, true, -1, maxNudge);
    }

    private static Float findDelta(float prevFacing, float target, int[] nudgeOut) {
        float base = target - prevFacing;
        if (Math.abs(base) < 180.0F && prevFacing + base == target) {
            nudgeOut[0] = 0;
            return Float.valueOf(base);
        }
        for (int u = 1; u <= 4; u++) {
            float up = base;
            for (int i = 0; i < u; i++) {
                up = Math.nextUp(up);
            }
            if (Math.abs(up) < 180.0F && prevFacing + up == target) {
                nudgeOut[0] = u;
                return Float.valueOf(up);
            }
            float dn = base;
            for (int i = 0; i < u; i++) {
                dn = Math.nextDown(dn);
            }
            if (Math.abs(dn) < 180.0F && prevFacing + dn == target) {
                nudgeOut[0] = u;
                return Float.valueOf(dn);
            }
        }
        return bracketSearch(prevFacing, target, base, nudgeOut);
    }

    private static Float bracketSearch(float prevFacing, float target, float base, int[] nudgeOut) {
        double lo = -180.0;
        double hi = 180.0;
        for (int it = 0; it < 200; it++) {
            double mid = 0.5 * (lo + hi);
            float d = (float) mid;
            float sum = prevFacing + d;
            if (sum == target && Math.abs(d) < 180.0F) {
                nudgeOut[0] = ulpsBetween(base, d);
                return Float.valueOf(d);
            }
            if (sum < target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        float pivot = (float) (0.5 * (lo + hi));
        for (int k = 0; k <= 32; k++) {
            float up = pivot;
            for (int i = 0; i < k; i++) {
                up = Math.nextUp(up);
            }
            if (Math.abs(up) < 180.0F && prevFacing + up == target) {
                nudgeOut[0] = ulpsBetween(base, up);
                return Float.valueOf(up);
            }
            float dn = pivot;
            for (int i = 0; i < k; i++) {
                dn = Math.nextDown(dn);
            }
            if (Math.abs(dn) < 180.0F && prevFacing + dn == target) {
                nudgeOut[0] = ulpsBetween(base, dn);
                return Float.valueOf(dn);
            }
        }
        return null;
    }

    private static int ulpsBetween(float a, float b) {
        int ia = Float.floatToIntBits(a);
        int ib = Float.floatToIntBits(b);
        if ((ia < 0) != (ib < 0)) {
            return Integer.MAX_VALUE;
        }
        long diff = Math.abs((long) ia - (long) ib);
        return diff > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) diff;
    }
}
