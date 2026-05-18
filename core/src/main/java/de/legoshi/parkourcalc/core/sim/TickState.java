package de.legoshi.parkourcalc.core.sim;

import java.util.Collections;
import java.util.List;

public final class TickState {

    public final Vec3dCore position;
    public final boolean onGround;
    public final boolean sneaking;
    public final boolean wallCollision;
    public final float yaw;
    public final List<Vec3dCore> subtickPath;

    // velocity is post-tick motionX/Y/Z (after MC's per-axis collision clamp), so on a wall-clamp
    // tick velocity.x may read 0 while the entity still moved on x. softCollision is the 1.21.10
    // Entity.collidedSoftly state (grazing wall hit that does not break sprint); always false on
    // legacy versions that don't model it.
    public final Vec3dCore velocity;
    public final boolean softCollision;

    public TickState(Vec3dCore position, boolean onGround, boolean sneaking, boolean wallCollision,
                     float yaw, List<Vec3dCore> subtickPath, Vec3dCore velocity, boolean softCollision) {
        this.position = position;
        this.onGround = onGround;
        this.sneaking = sneaking;
        this.wallCollision = wallCollision;
        this.yaw = yaw;
        this.subtickPath = subtickPath != null ? subtickPath : Collections.<Vec3dCore>emptyList();
        this.velocity = velocity != null ? velocity : Vec3dCore.ZERO;
        this.softCollision = softCollision;
    }
}
