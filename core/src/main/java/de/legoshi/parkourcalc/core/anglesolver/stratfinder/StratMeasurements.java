package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

public final class StratMeasurements {

    public String name;
    public String mcVersion;
    public int numTicks;
    public double startYawDeg;
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
    public double yawVelSdDeg;
    public double smoothTravelDeg;
    public int smoothReversals;
    public double smoothJerkDeg;
    public double smoothVelSdDeg;
    public double smoothMaxTurnDeg;
    public double[] smoothYaw;
    public long smoothMs;
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

    public int inputEdgesTotal() {
        return inputEdgesMomentum + inputEdgesJump;
    }

    public int turnTicksTotal() {
        return turnTicksMomentum + turnTicksJump;
    }
}
