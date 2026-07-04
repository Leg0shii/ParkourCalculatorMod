package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.sim.AABB;

import java.util.Collections;
import java.util.List;

public final class PickedBlock {

    public final int x;
    public final int y;
    public final int z;
    public final AABB box;
    public final List<AABB> boxes;

    public PickedBlock(int x, int y, int z, AABB box) {
        this(x, y, z, box, Collections.singletonList(box));
    }

    public PickedBlock(int x, int y, int z, AABB box, List<AABB> boxes) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.box = box;
        this.boxes = Collections.unmodifiableList(boxes);
    }
}
