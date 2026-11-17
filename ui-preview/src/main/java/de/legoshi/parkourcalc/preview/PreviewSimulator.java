package de.legoshi.parkourcalc.preview;

import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.Arrays;
import java.util.List;

final class PreviewSimulator implements Simulator {

    private static final double GROUND_FRICTION = 0.6 * 0.91;
    private static final double AIR_FRICTION = 0.91;

    private static final class State implements Checkpoint {
        double x, y, z, vx, vy, vz;
        double prevX, prevY, prevZ;
        float yaw, pitch;
        boolean onGround = true;
        boolean sprinting;
        boolean sneaking;
        boolean wall;
        float forward, strafe;

        State copy() {
            State s = new State();
            s.x = x; s.y = y; s.z = z;
            s.vx = vx; s.vy = vy; s.vz = vz;
            s.prevX = prevX; s.prevY = prevY; s.prevZ = prevZ;
            s.yaw = yaw; s.pitch = pitch;
            s.onGround = onGround; s.sprinting = sprinting; s.sneaking = sneaking; s.wall = wall;
            s.forward = forward; s.strafe = strafe;
            return s;
        }
    }

    private Vec3dCore startPos = new Vec3dCore(0.5, 64.0, 0.5);
    private Vec3dCore startVel = Vec3dCore.GROUND_REST_VELOCITY;
    private float startYaw;
    private float startPitch = PlaybackController.DEFAULT_PITCH;
    private double groundY = 64.0;
    private State state = new State();
    private boolean jumpHeld;

    @Override
    public void resetToStart() {
        state = new State();
        state.x = state.prevX = startPos.x;
        state.y = state.prevY = startPos.y;
        state.z = state.prevZ = startPos.z;
        state.vx = startVel.x;
        state.vy = startVel.y;
        state.vz = startVel.z;
        state.yaw = startYaw;
        state.pitch = startPitch;
        groundY = Math.floor(startPos.y * 16.0) / 16.0;
        state.onGround = startPos.y <= groundY + 1e-9;
    }

    @Override
    public void applyInput(InputRow row) {
        float forward = 0f;
        float strafe = 0f;
        if (row.isKeyActive(InputRow.Key.W)) forward += 1f;
        if (row.isKeyActive(InputRow.Key.S)) forward -= 1f;
        if (row.isKeyActive(InputRow.Key.A)) strafe += 1f;
        if (row.isKeyActive(InputRow.Key.D)) strafe -= 1f;
        state.sneaking = row.isKeyActive(InputRow.Key.SNEAK);
        if (state.sneaking) {
            forward *= 0.3f;
            strafe *= 0.3f;
        }
        state.forward = forward * 0.98f;
        state.strafe = strafe * 0.98f;
        state.sprinting = row.isKeyActive(InputRow.Key.SPRINT) && forward > 0 && !state.sneaking
                || state.sprinting && forward > 0 && !state.sneaking;
        jumpHeld = row.isKeyActive(InputRow.Key.JUMP);
        if (row.getYaw() != null) {
            state.yaw = row.isYawLocked() ? row.getYaw() : state.yaw + row.getYaw();
        }
        state.pitch = PlaybackController.applyPitch(state.pitch, row);
    }

    @Override
    public void tick() {
        State s = state;
        s.prevX = s.x;
        s.prevY = s.y;
        s.prevZ = s.z;
        double yawRad = Math.toRadians(s.yaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        if (jumpHeld && s.onGround) {
            s.vy = 0.42;
            if (s.sprinting) {
                s.vx -= sin * 0.2;
                s.vz += cos * 0.2;
            }
        }
        double accel = s.onGround
                ? 0.1 * (s.sprinting ? 1.3 : 1.0) * 0.16277136 / (GROUND_FRICTION * GROUND_FRICTION * GROUND_FRICTION)
                : (s.sprinting ? 0.026 : 0.02);
        double dist = s.strafe * s.strafe + s.forward * s.forward;
        if (dist >= 1.0E-4) {
            double scale = accel / Math.max(1.0, Math.sqrt(dist));
            double st = s.strafe * scale;
            double fw = s.forward * scale;
            s.vx += st * cos - fw * sin;
            s.vz += fw * cos + st * sin;
        }
        s.x += s.vx;
        s.z += s.vz;
        s.y += s.vy;
        if (s.y <= groundY) {
            s.y = groundY;
            s.vy = 0;
            s.onGround = true;
        } else {
            s.onGround = false;
        }
        double friction = s.onGround ? GROUND_FRICTION : AIR_FRICTION;
        s.vx *= friction;
        s.vz *= friction;
        s.vy = (s.vy - 0.08) * 0.98;
        if (Math.abs(s.vx) < 0.005) s.vx = 0;
        if (Math.abs(s.vz) < 0.005) s.vz = 0;
    }

    @Override
    public Vec3dCore getCurrentPosition() {
        return new Vec3dCore(state.x, state.y, state.z);
    }

    @Override
    public boolean isCurrentOnGround() {
        return state.onGround;
    }

    @Override
    public boolean isCurrentSneaking() {
        return state.sneaking;
    }

    @Override
    public boolean isCurrentSprinting() {
        return state.sprinting;
    }

    @Override
    public float getCurrentMoveForward() {
        return state.forward;
    }

    @Override
    public float getCurrentMoveStrafe() {
        return state.strafe;
    }

    @Override
    public boolean isCurrentWallCollision() {
        return state.wall;
    }

    @Override
    public Vec3dCore getCurrentVelocity() {
        return new Vec3dCore(state.vx, state.vy, state.vz);
    }

    @Override
    public boolean isCurrentSoftCollision() {
        return false;
    }

    @Override
    public double getCurrentCollisionAngleDegrees() {
        return Double.NaN;
    }

    @Override
    public float getCurrentYaw() {
        return state.yaw;
    }

    @Override
    public List<Vec3dCore> getCurrentSubtickPath() {
        return Arrays.asList(new Vec3dCore(state.prevX, state.prevY, state.prevZ), getCurrentPosition());
    }

    @Override
    public Vec3dCore getStartPosition() {
        return startPos;
    }

    @Override
    public void setStartPosition(Vec3dCore pos) {
        if (pos != null) startPos = pos;
    }

    @Override
    public Vec3dCore getStartVelocity() {
        return startVel;
    }

    @Override
    public void setStartVelocity(Vec3dCore vel) {
        if (vel != null) startVel = vel;
    }

    @Override
    public float getStartYaw() {
        return startYaw;
    }

    @Override
    public void setStartYaw(float yaw) {
        startYaw = yaw;
    }

    @Override
    public void setStartPitch(float pitch) {
        startPitch = pitch;
    }

    @Override
    public Checkpoint saveCheckpoint() {
        return state.copy();
    }

    @Override
    public void restoreCheckpoint(Checkpoint checkpoint) {
        if (checkpoint instanceof State) state = ((State) checkpoint).copy();
    }

    @Override
    public void invalidate() {
        state = new State();
    }
}
