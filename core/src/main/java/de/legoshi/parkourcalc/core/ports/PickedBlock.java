package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.sim.AABB;

public final class PickedBlock {

    public final int x;
    public final int y;
    public final int z;
    public final AABB box;

    public PickedBlock(int x, int y, int z, AABB box) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.box = box;
    }
}
