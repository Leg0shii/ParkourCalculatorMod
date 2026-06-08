package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.sim.AABB;

/** A block the player's crosshair is on: its integer coords plus the real world-space hitbox
 *  (collision/outline shape) the loader read for it. Returned by {@link BlockPicker} so core can
 *  derive solver constraints from real block shapes (full cubes, slabs, heads) without MC types. */
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
