package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.List;

public interface Simulator {

    void resetToStart();

    void applyInput(InputRow row);

    void tick();

    Vec3dCore getCurrentPosition();

    boolean isCurrentOnGround();

    boolean isCurrentSneaking();

    boolean isCurrentSprinting();

    /** The moveFlying inputs the last tick ran with (post input pipeline, sneak scaling included). */
    float getCurrentMoveForward();

    float getCurrentMoveStrafe();

    boolean isCurrentWallCollision();

    Vec3dCore getCurrentVelocity();

    boolean isCurrentSoftCollision();

    double getCurrentCollisionAngleDegrees();

    float getCurrentYaw();

    List<Vec3dCore> getCurrentSubtickPath();

    Vec3dCore getStartPosition();

    void setStartPosition(Vec3dCore pos);

    Vec3dCore getStartVelocity();

    void setStartVelocity(Vec3dCore vel);

    float getStartYaw();

    void setStartYaw(float yaw);

    Checkpoint saveCheckpoint();

    void restoreCheckpoint(Checkpoint checkpoint);

    void invalidate();
}
