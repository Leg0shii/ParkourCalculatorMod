package de.legoshi.parkourcalc.anglesolver.metriclab;

import java.util.Locale;

public final class JumpMeasurements {

    public String name;
    public int dLevel;
    public String mcVersion;
    public int numTicks;
    public int jumps;
    public int takeoffTick;
    public int facingConstraints;
    public double minMargin;
    public double[] windowLo;
    public double[] windowHi;
    public double jitterDeg;
    public double centerDriftDeg;
    public double winMinDeg;
    public double winGeoDeg;
    public double winMinMomentumDeg;
    public double winGeoMomentumDeg;
    public double winMinJumpDeg;
    public double winGeoJumpDeg;
    public int inputEdgesMomentum;
    public int inputEdgesJump;
    public int turnTicksMomentum;
    public int turnTicksJump;
    public boolean jumpAngle;
    public double yawTravelDeg;
    public int yawReversals;
    public double yawJerkDeg;
    public int[] shiftEdgeRow;
    public boolean[] shiftEdgeMomentum;
    public String[] shiftEdgeKeys;
    public int[] shiftLo;
    public int[] shiftHi;
    public boolean[] shiftLoCensored;
    public boolean[] shiftHiCensored;
    public boolean[] shiftLoFree;
    public boolean[] shiftHiFree;
    public double shiftMinMomentumTicks;
    public double shiftGeoMomentumTicks;
    public double shiftMinJumpTicks;
    public double shiftGeoJumpTicks;
    public double shiftGeoMomentumEffTicks;
    public double shiftGeoJumpEffTicks;
    public long shiftMs;
    public SampleMeta meta;

    public int inputEdgesTotal() {
        return inputEdgesMomentum + inputEdgesJump;
    }

    public int turnTicksTotal() {
        return turnTicksMomentum + turnTicksJump;
    }

    public static String csvHeader() {
        return "name,dLevel,mcVersion,numTicks,jumps,takeoffTick,facingConstraints,minMargin,jitterDeg,centerDriftDeg,winMinDeg,winGeoDeg,"
                + "winMinMomentumDeg,winGeoMomentumDeg,winMinJumpDeg,winGeoJumpDeg,"
                + "inputEdgesMomentum,inputEdgesJump,turnTicksMomentum,turnTicksJump,jumpAngle,"
                + "yawTravelDeg,yawReversals,yawJerkDeg,"
                + "shiftMinMomentumTicks,shiftGeoMomentumTicks,shiftMinJumpTicks,shiftGeoJumpTicks,"
                + "shiftGeoMomentumEffTicks,shiftGeoJumpEffTicks,"
                + "subTier,jumpClass,rung";
    }

    public String csvRow() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(',');
        sb.append(dLevel).append(',');
        sb.append(mcVersion).append(',');
        sb.append(numTicks).append(',');
        sb.append(jumps).append(',');
        sb.append(takeoffTick).append(',');
        sb.append(facingConstraints).append(',');
        sb.append(num(minMargin)).append(',');
        sb.append(num(jitterDeg)).append(',');
        sb.append(num(centerDriftDeg)).append(',');
        sb.append(num(winMinDeg)).append(',');
        sb.append(num(winGeoDeg)).append(',');
        sb.append(num(winMinMomentumDeg)).append(',');
        sb.append(num(winGeoMomentumDeg)).append(',');
        sb.append(num(winMinJumpDeg)).append(',');
        sb.append(num(winGeoJumpDeg)).append(',');
        sb.append(inputEdgesMomentum).append(',');
        sb.append(inputEdgesJump).append(',');
        sb.append(turnTicksMomentum).append(',');
        sb.append(turnTicksJump).append(',');
        sb.append(jumpAngle).append(',');
        sb.append(num(yawTravelDeg)).append(',');
        sb.append(yawReversals).append(',');
        sb.append(num(yawJerkDeg)).append(',');
        sb.append(num(shiftMinMomentumTicks)).append(',');
        sb.append(num(shiftGeoMomentumTicks)).append(',');
        sb.append(num(shiftMinJumpTicks)).append(',');
        sb.append(num(shiftGeoJumpTicks)).append(',');
        sb.append(num(shiftGeoMomentumEffTicks)).append(',');
        sb.append(num(shiftGeoJumpEffTicks)).append(',');
        sb.append(str(meta != null ? meta.subTier : null)).append(',');
        sb.append(str(meta != null ? meta.jumpClass : null)).append(',');
        sb.append(str(meta != null ? meta.rung : null));
        return sb.toString();
    }

    private static String num(double v) {
        if (Double.isNaN(v)) {
            return "";
        }
        return String.format(Locale.ROOT, "%.6f", v);
    }

    private static String str(String s) {
        return s != null ? s : "";
    }
}
