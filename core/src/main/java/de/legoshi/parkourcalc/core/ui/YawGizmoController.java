package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.function.Consumer;

/**
 * Right-click drag-to-rotate state machine for tick boxes. Mirrors
 * BoxDragController: each loader feeds per-frame (rayOrigin, rayDirection,
 * mousePressedRight, ctrlDown, cursorScreenX, cursorScreenY, uiFocused) and the
 * chosen yaw flows back through onStartYawChange (box 0) or onTickYawChange
 * (box i, writes to rows[i] so the delta lands on box i+1). Holding ctrl at
 * press time switches the drag to pitch mode: the cursor projects onto the
 * vertical plane along the box's facing and the pitch flows through
 * onStartPitchChange / onTickPitchChange instead. Loaders also call
 * isCursorOverAnyBox() / isEngaged() from their right-click suppression hook so
 * block-place / item-use is swallowed while the gizmo is active.
 */
public final class YawGizmoController {

    public interface TickAngleSink {
        void accept(int rowIndex, float degrees);
    }

    private final BoxController boxController;
    private final Consumer<Float> onStartYawChange;
    private final TickAngleSink onTickYawChange;
    private final Consumer<Float> onStartPitchChange;
    private final TickAngleSink onTickPitchChange;

    private boolean wasMousePressed = false;
    private State state = null;

    public YawGizmoController(BoxController boxController, Consumer<Float> onStartYawChange, TickAngleSink onTickYawChange,
                              Consumer<Float> onStartPitchChange, TickAngleSink onTickPitchChange) {
        this.boxController = boxController;
        this.onStartYawChange = onStartYawChange;
        this.onTickYawChange = onTickYawChange;
        this.onStartPitchChange = onStartPitchChange;
        this.onTickPitchChange = onTickPitchChange;
    }

    public void tick(Vec3dCore rayOrigin, Vec3dCore rayDirection, boolean mousePressed, boolean ctrlDown, double cursorScreenX, double cursorScreenY, boolean uiFocused) {
        if (uiFocused) {
            wasMousePressed = false;
            state = null;
            return;
        }

        if (mousePressed && !wasMousePressed) {
            tryStart(rayOrigin, rayDirection, ctrlDown, cursorScreenX, cursorScreenY);
        }
        if (mousePressed && state != null) {
            update(rayOrigin, rayDirection, cursorScreenX, cursorScreenY);
        }
        if (!mousePressed) {
            state = null;
        }
        wasMousePressed = mousePressed;
    }

    public boolean isEngaged() {
        return state != null && state.engaged;
    }

    public int getSelectedIndex() {
        return state == null ? -1 : state.boxIndex;
    }

    public Float getCurrentYawDegrees() {
        return state == null ? null : state.lastEmittedYaw;
    }

    public boolean isPitchMode() {
        return state != null && state.pitchMode;
    }

    public float getPlaneYawDegrees() {
        return state == null ? 0f : state.planeYawDeg;
    }

    public float getGizmoPitchDegrees() {
        if (state == null) return 0f;
        return state.lastEmittedPitch != null ? state.lastEmittedPitch : state.initialPitchDeg;
    }

    public boolean isCursorOverAnyBox(Vec3dCore rayOrigin, Vec3dCore rayDirection) {
        return boxController.pickBoxIndex(rayOrigin, rayDirection) >= 0;
    }

    private void tryStart(Vec3dCore origin, Vec3dCore direction, boolean ctrlDown, double sx, double sy) {
        int idx = boxController.pickBoxIndex(origin, direction);
        if (idx < 0) return;

        Vec3dCore center = boxController.getCenter(idx);
        if (center == null) return;

        if (idx > 0 && idx + 1 >= boxController.size()) return;

        state = new State(idx, center.x, center.y, center.z, sx, sy,
                ctrlDown, boxController.getYaw(idx), boxController.getPitch(idx));
    }

    private void update(Vec3dCore origin, Vec3dCore direction, double sx, double sy) {
        if (!state.engaged) {
            if (!TapThreshold.exceeded(state.pressScreenX, state.pressScreenY, sx, sy)) return;
            state.engaged = true;
        }

        if (state.pitchMode) {
            updatePitch(origin, direction);
        } else {
            updateYaw(origin, direction);
        }
    }

    private void updateYaw(Vec3dCore origin, Vec3dCore direction) {
        Vec3dCore cursorOnPlane = projectCursorToPlane(origin, direction, state.centerY);
        if (cursorOnPlane == null) return;

        double dx = cursorOnPlane.x - state.centerX;
        double dz = cursorOnPlane.z - state.centerZ;
        if (dx * dx + dz * dz < 1.0e-10) return;

        float yawDeg = (float) Math.toDegrees(Math.atan2(-dx, dz));
        yawDeg = ((yawDeg % 360.0f) + 540.0f) % 360.0f - 180.0f;

        if (state.lastEmittedYaw != null && Float.compare(state.lastEmittedYaw, yawDeg) == 0) return;
        state.lastEmittedYaw = yawDeg;

        if (state.boxIndex == 0) {
            onStartYawChange.accept(yawDeg);
        } else {
            onTickYawChange.accept(state.boxIndex, yawDeg);
        }
    }

    private void updatePitch(Vec3dCore origin, Vec3dCore direction) {
        double yawRad = Math.toRadians(state.planeYawDeg);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double nx = -fz;
        double nz = fx;

        double denom = direction.x * nx + direction.z * nz;
        if (Math.abs(denom) < 1.0e-6) return;
        double t = ((state.centerX - origin.x) * nx + (state.centerZ - origin.z) * nz) / denom;
        if (t < 0) return;

        double px = origin.x + direction.x * t - state.centerX;
        double py = origin.y + direction.y * t - state.centerY;
        double pz = origin.z + direction.z * t - state.centerZ;
        double forward = px * fx + pz * fz;
        if (forward * forward + py * py < 1.0e-10) return;

        float pitchDeg = (float) Math.toDegrees(Math.atan2(-py, forward));
        if (pitchDeg > 90.0f) pitchDeg = 90.0f;
        if (pitchDeg < -90.0f) pitchDeg = -90.0f;

        if (state.lastEmittedPitch != null && Float.compare(state.lastEmittedPitch, pitchDeg) == 0) return;
        state.lastEmittedPitch = pitchDeg;

        if (state.boxIndex == 0) {
            onStartPitchChange.accept(pitchDeg);
        } else {
            onTickPitchChange.accept(state.boxIndex, pitchDeg);
        }
    }

    private static Vec3dCore projectCursorToPlane(Vec3dCore origin, Vec3dCore direction, double planeY) {
        if (Math.abs(direction.y) < 1.0e-6) return null;
        double t = (planeY - origin.y) / direction.y;
        if (t < 0) return null;
        return new Vec3dCore(origin.x + direction.x * t, planeY, origin.z + direction.z * t);
    }

    private static final class State {
        final int boxIndex;
        final double centerX;
        final double centerY;
        final double centerZ;
        final double pressScreenX;
        final double pressScreenY;
        final boolean pitchMode;
        final float planeYawDeg;
        final float initialPitchDeg;
        boolean engaged;
        Float lastEmittedYaw;
        Float lastEmittedPitch;

        State(int boxIndex, double cx, double cy, double cz, double sx, double sy,
              boolean pitchMode, float planeYawDeg, float initialPitchDeg) {
            this.boxIndex = boxIndex;
            this.centerX = cx;
            this.centerY = cy;
            this.centerZ = cz;
            this.pressScreenX = sx;
            this.pressScreenY = sy;
            this.pitchMode = pitchMode;
            this.planeYawDeg = planeYawDeg;
            this.initialPitchDeg = initialPitchDeg;
        }
    }
}
