package de.legoshi.parkourcalc.core.anglesolver.solver;

/** Per-tick position arrays of length n+1, where index 0 is the start state and index n
 *  is the state after the n-th tick. */
public final class ForwardPath {

    public final double[] posX;
    public final double[] posY;
    public final double[] posZ;

    public ForwardPath(double[] posX, double[] posY, double[] posZ) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
    }

    public double getPos(int tick, JumpPhysicsInputs.Axis axis) {
        switch (axis) {
            case X: return posX[tick];
            case Y: return posY[tick];
            case Z: return posZ[tick];
            default: throw new IllegalArgumentException("axis=" + axis);
        }
    }
}
