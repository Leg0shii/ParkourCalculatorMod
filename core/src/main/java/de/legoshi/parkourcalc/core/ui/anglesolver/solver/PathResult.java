package de.legoshi.parkourcalc.core.ui.anglesolver.solver;

/** Per-tick state arrays of length n+1, where index 0 is the start state and index n
 *  is the state after the n-th tick. Models that don't simulate vertical physics fill
 *  posY/velY with the scenario start y and zero respectively. */
public final class PathResult {

    public final double[] posX;
    public final double[] posY;
    public final double[] posZ;
    public final double[] velX;
    public final double[] velY;
    public final double[] velZ;

    public PathResult(double[] posX, double[] posY, double[] posZ,
                      double[] velX, double[] velY, double[] velZ) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.velX = velX;
        this.velY = velY;
        this.velZ = velZ;
    }

    public double getPos(int tick, Spike0Scenario.Axis axis) {
        switch (axis) {
            case X: return posX[tick];
            case Y: return posY[tick];
            case Z: return posZ[tick];
            default: throw new IllegalArgumentException("axis=" + axis);
        }
    }
}
