package de.legoshi.parkourcalc.core.sim;

public final class BlockHit {

    public final int x;
    public final int y;
    public final int z;
    public final Face face;
    public final Vec3dCore hitVec;

    public BlockHit(int x, int y, int z, Face face, Vec3dCore hitVec) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.face = face;
        this.hitVec = hitVec;
    }
}
