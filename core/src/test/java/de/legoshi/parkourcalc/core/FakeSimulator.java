package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.Collections;
import java.util.List;

public class FakeSimulator implements Simulator {

    private Vec3dCore startPos = Vec3dCore.ZERO;
    private Vec3dCore startVel = Vec3dCore.ZERO;
    private float startYaw;

    @Override public void resetToStart() { }
    @Override public void applyInput(InputRow row) { }
    @Override public void tick() { }
    @Override public Vec3dCore getCurrentPosition() { return Vec3dCore.ZERO; }
    @Override public boolean isCurrentOnGround() { return false; }
    @Override public boolean isCurrentSneaking() { return false; }
    @Override public boolean isCurrentSprinting() { return false; }
    @Override public float getCurrentMoveForward() { return Float.NaN; }
    @Override public float getCurrentMoveStrafe() { return Float.NaN; }
    @Override public boolean isCurrentWallCollision() { return false; }
    @Override public Vec3dCore getCurrentVelocity() { return Vec3dCore.ZERO; }
    @Override public boolean isCurrentSoftCollision() { return false; }
    @Override public double getCurrentCollisionAngleDegrees() { return Double.NaN; }
    @Override public float getCurrentYaw() { return 0f; }
    @Override public List<Vec3dCore> getCurrentSubtickPath() { return Collections.emptyList(); }
    @Override public Vec3dCore getStartPosition() { return startPos; }
    @Override public void setStartPosition(Vec3dCore pos) { startPos = pos; }
    @Override public Vec3dCore getStartVelocity() { return startVel; }
    @Override public void setStartVelocity(Vec3dCore vel) { startVel = vel; }
    @Override public float getStartYaw() { return startYaw; }
    @Override public void setStartYaw(float yaw) { startYaw = yaw; }
    @Override public Checkpoint saveCheckpoint() { return null; }
    @Override public void restoreCheckpoint(Checkpoint checkpoint) { }
    @Override public void invalidate() { }
}
