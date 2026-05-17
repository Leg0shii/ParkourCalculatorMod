package de.legoshi.parkourcalc.core.sim;

public final class TickState {

    public final Vec3dCore position;
    public final boolean onGround;
    public final boolean sneaking;
    public final boolean wallCollision;

    public TickState(Vec3dCore position, boolean onGround, boolean sneaking, boolean wallCollision) {
        this.position = position;
        this.onGround = onGround;
        this.sneaking = sneaking;
        this.wallCollision = wallCollision;
    }
}
