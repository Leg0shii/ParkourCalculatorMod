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
    // legacy versions that don't model it. collisionAngleDegrees is the angle MC's hasCollidedSoftly
    // computes between intended motion (forwardSpeed/sidewaysSpeed rotated by yaw) and post-collision
    // adjusted motion; NaN on legacy loaders and when MC's 1e-5 speed gate skips it.
    public final Vec3dCore velocity;
    public final boolean softCollision;
    public final double collisionAngleDegrees;

    // gh-120: the entity state the tick actually ran with, sampled post-tick so the solver reads the
    // version-correct sprint flag and moveFlying inputs (sneak scaling included) instead of rederiving
    // them from the rows. moveForward/moveStrafe are NaN when no sample was taken (legacy callers);
    // moveStrafe is positive toward A (left), matching MC.
    public final boolean sprinting;
    public final float moveForward;
    public final float moveStrafe;

    public TickState(Vec3dCore position, boolean onGround, boolean sneaking, boolean wallCollision, float yaw, List<Vec3dCore> subtickPath, Vec3dCore velocity, boolean softCollision, double collisionAngleDegrees) {
        this(position, onGround, sneaking, wallCollision, yaw, subtickPath, velocity, softCollision, collisionAngleDegrees,
                false, Float.NaN, Float.NaN);
    }

    public TickState(Vec3dCore position, boolean onGround, boolean sneaking, boolean wallCollision, float yaw, List<Vec3dCore> subtickPath, Vec3dCore velocity, boolean softCollision, double collisionAngleDegrees,
                     boolean sprinting, float moveForward, float moveStrafe) {
        this.position = position;
        this.onGround = onGround;
        this.sneaking = sneaking;
        this.wallCollision = wallCollision;
        this.yaw = yaw;
        this.subtickPath = subtickPath != null ? subtickPath : Collections.emptyList();
        this.velocity = velocity != null ? velocity : Vec3dCore.ZERO;
        this.softCollision = softCollision;
        this.collisionAngleDegrees = collisionAngleDegrees;
        this.sprinting = sprinting;
        this.moveForward = moveForward;
        this.moveStrafe = moveStrafe;
    }

    /** Whether this state carries the post-tick movement sample (sprint + moveFlying inputs). */
    public boolean hasMovementSample() {
        return !Float.isNaN(moveForward);
    }
}
