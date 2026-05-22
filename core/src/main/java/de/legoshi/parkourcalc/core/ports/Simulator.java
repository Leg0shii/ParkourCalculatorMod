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

    boolean isCurrentWallCollision();

    Vec3dCore getCurrentVelocity();

    boolean isCurrentSoftCollision();

    float getCurrentYaw();

    List<Vec3dCore> getCurrentSubtickPath();

    Vec3dCore getStartPosition();

    void setStartPosition(Vec3dCore pos);

    Vec3dCore getStartVelocity();

    void setStartVelocity(Vec3dCore vel);

    float getStartYaw();

    void setStartYaw(float yaw);

    /** Snapshot all mutable simulator state needed to resume ticking from the current point.
     *  Returned token is opaque to callers; only restoreCheckpoint understands it. */
    Checkpoint saveCheckpoint();

    /** Restore a snapshot taken by saveCheckpoint. Token must come from this same simulator. */
    void restoreCheckpoint(Checkpoint checkpoint);
}
