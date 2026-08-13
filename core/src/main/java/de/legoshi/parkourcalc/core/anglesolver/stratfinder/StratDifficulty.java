package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

public final class StratDifficulty {

    public static final double SIG_ANGLE_DEG = 360.0 / 65536.0;
    public static final double TIGHT_MARGIN = 5.0e-5;

    private StratDifficulty() {
    }

    public static double combinedV4(StratMeasurements m) {
        return toleranceCore(m)
                + 0.8 * effShiftDemandSum(m)
                + 0.06 * m.turnTicksJump
                + (m.jumpAngle ? 1.5 : 0.0)
                + (m.minMargin < TIGHT_MARGIN ? 1.5 : 0.0)
                + 0.15 * Math.log1p(m.smoothJerkDeg);
    }

    public static double toleranceCore(StratMeasurements m) {
        double jitter = Math.max(m.jitterDeg, SIG_ANGLE_DEG);
        double geo = Math.max(m.winGeoDeg, SIG_ANGLE_DEG);
        return -Math.log10(jitter) - 0.5 * Math.log10(geo);
    }

    public static double effShiftDemandSum(StratMeasurements m) {
        if (m.shiftEdgeRow == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 0; i < m.shiftEdgeRow.length; i++) {
            if (m.shiftLoFree[i] || m.shiftHiFree[i]) {
                continue;
            }
            sum += shiftDemand(m.shiftLo[i] + m.shiftHi[i]);
        }
        return sum;
    }

    public static double shiftDemand(double widthTicks) {
        if (Double.isNaN(widthTicks)) {
            return 0.0;
        }
        double full = 2.0 * StratMeasure.SHIFT_CAP_TICKS + 1.0;
        return Math.log10(full / (1.0 + widthTicks));
    }
}
