package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

/** One block the user picked for the Angle Solver: its role plus the real world-space hitbox captured
 *  at pick time. The generator turns these into per-tick {@link Constraint}s and the objective. */
public final class BlockSelection {

    public enum Kind {
        START,
        COLLISION,
        LAND
    }

    public final Kind kind;
    public final int x;
    public final int y;
    public final int z;
    public final AABB box;

    public BlockSelection(Kind kind, int x, int y, int z, AABB box) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.box = box;
    }

    /** Full cube hitbox at integer coords; fallback when a loader can't read a real shape. */
    public static BlockSelection cube(Kind kind, int x, int y, int z) {
        AABB box = new AABB(new Vec3dCore(x, y, z), new Vec3dCore(x + 1.0, y + 1.0, z + 1.0));
        return new BlockSelection(kind, x, y, z, box);
    }

    public String coordLabel() {
        return x + ", " + y + ", " + z;
    }
}
