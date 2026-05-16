package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.function.Consumer;

/**
 * Loader-agnostic drag state machine for BoxController.getFirst(). Each loader
 * feeds per-frame (rayOrigin, rayDirection, mousePressed, uiFocused) and hands
 * Vec3dCore deltas back to its simulator via the position-change callback.
 * Loaders also call isDragging() / isCursorOverStartBox() from their attack-
 * suppression hook so a press on the start box is consumed before MC mines.
 */
public final class BoxDragController {

    private static final double PICK_REACH = 128.0;
    private static final double EPS = 1.0e-3;

    private final BoxController boxController;
    private final Consumer<Vec3dCore> onPositionChange;

    private boolean wasMousePressed = false;
    private DragState dragState = null;

    public BoxDragController(BoxController boxController, Consumer<Vec3dCore> onPositionChange) {
        this.boxController = boxController;
        this.onPositionChange = onPositionChange;
    }

    public void tick(Vec3dCore rayOrigin, Vec3dCore rayDirection, boolean mousePressed, boolean uiFocused) {
        if (uiFocused) {
            wasMousePressed = false;
            dragState = null;
            return;
        }
        if (mousePressed && !wasMousePressed) {
            tryStartDrag(rayOrigin, rayDirection);
        }
        if (mousePressed && dragState != null) {
            updateDrag(rayOrigin, rayDirection);
        }
        if (!mousePressed) {
            dragState = null;
        }
        wasMousePressed = mousePressed;
    }

    public boolean isDragging() {
        return dragState != null;
    }

    /** True if rayOrigin+rayDirection currently intersects the start box. Used to swallow left-click presses before MC sees them. */
    public boolean isCursorOverStartBox(Vec3dCore rayOrigin, Vec3dCore rayDirection) {
        AABB first = boxController.getFirst();
        if (first == null) return false;
        return rayHitsAabb(rayOrigin, rayDirection, expand(first, EPS), PICK_REACH);
    }

    private void tryStartDrag(Vec3dCore origin, Vec3dCore direction) {
        AABB first = boxController.getFirst();
        if (first == null) return;
        if (!rayHitsAabb(origin, direction, expand(first, EPS), PICK_REACH)) return;

        Vec3dCore cursorOnPlane = projectCursorToPlane(origin, direction, first.min.y);
        if (cursorOnPlane == null) return;

        dragState = new DragState(
                first.min.y,
                first.min.x, first.min.z,
                cursorOnPlane.x, cursorOnPlane.z,
                new Vec3dCore(first.min.x, first.min.y, first.min.z)
        );
    }

    private void updateDrag(Vec3dCore origin, Vec3dCore direction) {
        Vec3dCore cursorOnPlane = projectCursorToPlane(origin, direction, dragState.planeY);
        if (cursorOnPlane == null) return;

        double dx = cursorOnPlane.x - dragState.startCursorX;
        double dz = cursorOnPlane.z - dragState.startCursorZ;

        Vec3dCore newPos = new Vec3dCore(
                dragState.startBoxX + dx,
                dragState.planeY,
                dragState.startBoxZ + dz
        );
        if (newPos.equals(dragState.lastEmittedPos)) return;
        onPositionChange.accept(newPos);
        dragState.lastEmittedPos = newPos;
    }

    private static AABB expand(AABB box, double e) {
        return new AABB(
                new Vec3dCore(box.min.x - e, box.min.y - e, box.min.z - e),
                new Vec3dCore(box.max.x + e, box.max.y + e, box.max.z + e)
        );
    }

    private static Vec3dCore projectCursorToPlane(Vec3dCore origin, Vec3dCore direction, double planeY) {
        if (Math.abs(direction.y) < 1.0e-6) return null;
        double t = (planeY - origin.y) / direction.y;
        if (t < 0) return null;
        return new Vec3dCore(origin.x + direction.x * t, origin.y + direction.y * t, origin.z + direction.z * t);
    }

    private static boolean rayHitsAabb(Vec3dCore o, Vec3dCore d, AABB box, double maxT) {
        double tmin = 0.0;
        double tmax = maxT;

        double[] xRange = slab(o.x, d.x, box.min.x, box.max.x);
        if (xRange == null) return false;
        tmin = Math.max(tmin, xRange[0]);
        tmax = Math.min(tmax, xRange[1]);
        if (tmax < tmin) return false;

        double[] yRange = slab(o.y, d.y, box.min.y, box.max.y);
        if (yRange == null) return false;
        tmin = Math.max(tmin, yRange[0]);
        tmax = Math.min(tmax, yRange[1]);
        if (tmax < tmin) return false;

        double[] zRange = slab(o.z, d.z, box.min.z, box.max.z);
        if (zRange == null) return false;
        tmin = Math.max(tmin, zRange[0]);
        tmax = Math.min(tmax, zRange[1]);
        return tmax >= tmin;
    }

    private static double[] slab(double o, double d, double min, double max) {
        if (Math.abs(d) < 1.0e-12) {
            if (o < min || o > max) return null;
            return new double[]{Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
        }
        double t1 = (min - o) / d;
        double t2 = (max - o) / d;
        return t1 < t2 ? new double[]{t1, t2} : new double[]{t2, t1};
    }

    private static final class DragState {
        final double planeY;
        final double startBoxX;
        final double startBoxZ;
        final double startCursorX;
        final double startCursorZ;
        Vec3dCore lastEmittedPos;

        DragState(double planeY, double startBoxX, double startBoxZ, double startCursorX, double startCursorZ,
                  Vec3dCore initialBoxPos) {
            this.planeY = planeY;
            this.startBoxX = startBoxX;
            this.startBoxZ = startBoxZ;
            this.startCursorX = startCursorX;
            this.startCursorZ = startCursorZ;
            this.lastEmittedPos = initialBoxPos;
        }
    }
}
