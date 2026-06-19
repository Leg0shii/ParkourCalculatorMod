package de.legoshi.parkourcalc.core.render;

import de.legoshi.parkourcalc.core.sim.AABB;

import java.util.List;

public final class ConstraintPlate {

    public final ConstraintShapes.Sense sense;
    public final boolean satisfied;
    public final List<AABB> front;
    public final List<AABB> back;
    public final int tick;
    public final int[] constraintIndices;
    public boolean highlighted;

    public ConstraintPlate(ConstraintShapes.Sense sense, boolean satisfied, List<AABB> front, List<AABB> back, int tick, int[] constraintIndices) {
        this.sense = sense;
        this.satisfied = satisfied;
        this.front = front;
        this.back = back;
        this.tick = tick;
        this.constraintIndices = constraintIndices;
    }
}
