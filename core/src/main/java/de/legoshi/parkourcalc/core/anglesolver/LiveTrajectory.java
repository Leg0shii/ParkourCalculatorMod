package de.legoshi.parkourcalc.core.anglesolver;

public final class LiveTrajectory {

    public final long seq;
    public final int startTick;
    public final double[] posX;
    public final double[] posZ;
    public final boolean feasible;

    public LiveTrajectory(long seq, int startTick, double[] posX, double[] posZ, boolean feasible) {
        this.seq = seq;
        this.startTick = startTick;
        this.posX = posX;
        this.posZ = posZ;
        this.feasible = feasible;
    }

    public int pointCount() {
        return posX.length;
    }
}
