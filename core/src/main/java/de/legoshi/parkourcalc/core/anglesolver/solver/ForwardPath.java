package de.legoshi.parkourcalc.core.anglesolver.solver;

/** Per-tick position arrays of length n+1, where index 0 is the start state and index n
 *  is the state after the n-th tick.
 *
 *  <p>Optional per-tick velocity arrays (the post-friction carry that seeds the next tick) are exposed
 *  for chaining segments end-to-end on the byte-exact model: a segment's exit velocity {@code vel[k]}
 *  seeds the next segment's {@link JumpPhysicsInputs#initialVelocity}. {@code vel} is null when the
 *  forward did not record it (callers that only read positions are unaffected). */
public final class ForwardPath {

    public final double[] posX;
    public final double[] posY;
    public final double[] posZ;

    /** Post-friction carry velocity at each tick boundary (length n+1), or null if not recorded. */
    public final double[] velX;
    public final double[] velY;
    public final double[] velZ;

    public ForwardPath(double[] posX, double[] posY, double[] posZ) {
        this(posX, posY, posZ, null, null, null);
    }

    public ForwardPath(double[] posX, double[] posY, double[] posZ, double[] velX, double[] velY, double[] velZ) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.velX = velX;
        this.velY = velY;
        this.velZ = velZ;
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
