package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.function.Consumer;

/**
 * Right-click drag-to-rotate state machine for tick boxes. Mirrors
 * BoxDragController: each loader feeds per-frame (rayOrigin, rayDirection,
 * mousePressedRight, cursorScreenX, cursorScreenY, uiFocused) and the chosen
 * yaw flows back through onStartYawChange (box 0) or onTickYawChange (box i,
 * writes to InputRow at row i-1). Loaders also call isCursorOverAnyBox() /
 * isEngaged() from their right-click suppression hook so block-place / item-use
 * is swallowed while the gizmo is active.
 */
public final class YawGizmoController {

    public interface TickYawSink {
        void accept(int rowIndex, float yawDegrees);
    }

    private static final double ENGAGE_THRESHOLD_PX = 2.0;

    private final BoxController boxController;
    private final Consumer<Float> onStartYawChange;
    private final TickYawSink onTickYawChange;

    private boolean wasMousePressed = false;
    private State state = null;

    public YawGizmoController(BoxController boxController,
                              Consumer<Float> onStartYawChange,
                              TickYawSink onTickYawChange) {
        this.boxController = boxController;
        this.onStartYawChange = onStartYawChange;
        this.onTickYawChange = onTickYawChange;
    }

    public void tick(Vec3dCore rayOrigin, Vec3dCore rayDirection,
                     boolean mousePressed,
                     double cursorScreenX, double cursorScreenY,
                     boolean uiFocused) {
        if (uiFocused) {
            wasMousePressed = false;
            state = null;
            return;
        }

        if (mousePressed && !wasMousePressed) {
            tryStart(rayOrigin, rayDirection, cursorScreenX, cursorScreenY);
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

    public boolean isCursorOverAnyBox(Vec3dCore rayOrigin, Vec3dCore rayDirection) {
        return boxController.pickBoxIndex(rayOrigin, rayDirection) >= 0;
    }

    private void tryStart(Vec3dCore origin, Vec3dCore direction, double sx, double sy) {
        int idx = boxController.pickBoxIndex(origin, direction);
        if (idx < 0) return;

        Vec3dCore center = boxController.getCenter(idx);
        if (center == null) return;

        if (idx > 0 && idx + 1 >= boxController.size()) return;

        state = new State(idx, center.x, center.y, center.z, sx, sy);
    }

    private void update(Vec3dCore origin, Vec3dCore direction, double sx, double sy) {
        if (!state.engaged) {
            double dxPx = sx - state.pressScreenX;
            double dyPx = sy - state.pressScreenY;
            if (Math.hypot(dxPx, dyPx) < ENGAGE_THRESHOLD_PX) return;
            state.engaged = true;
        }

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
        boolean engaged;
        Float lastEmittedYaw;

        State(int boxIndex, double cx, double cy, double cz, double sx, double sy) {
            this.boxIndex = boxIndex;
            this.centerX = cx;
            this.centerY = cy;
            this.centerZ = cz;
            this.pressScreenX = sx;
            this.pressScreenY = sy;
        }
    }
}
