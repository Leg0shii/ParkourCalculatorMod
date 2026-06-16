package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

/**
 * Loader-agnostic drag state machine for BoxController.getFirst(). Each loader
 * feeds per-frame (rayOrigin, rayDirection, mousePressed, uiFocused) and hands
 * Vec3dCore deltas back to its simulator via the position-change callback.
 * Loaders also call isDragging() / isCursorOverStartBox() from their attack-
 * suppression hook so a press on the start box is consumed before MC mines.
 */
public final class BoxDragController {

    public interface StartDragHandler {
        void onBegin(boolean rigid);

        void onMove(Vec3dCore position, boolean rigid);

        void onEnd(boolean rigid);
    }

    private static final double PICK_REACH = 128.0;
    private static final double EPS = 1.0e-3;

    private final BoxController boxController;
    private final StartDragHandler handler;
    private final Runnable onStartBoxTap;

    private boolean wasMousePressed = false;
    private boolean pressedOverStartBox = false;
    private double pressScreenX = 0.0;
    private double pressScreenY = 0.0;
    private boolean engaged = false;
    private DragState dragState = null;

    public BoxDragController(BoxController boxController, StartDragHandler handler, Runnable onStartBoxTap) {
        this.boxController = boxController;
        this.handler = handler;
        this.onStartBoxTap = onStartBoxTap;
    }

    public void tick(Vec3dCore rayOrigin, Vec3dCore rayDirection, boolean mousePressed, double cursorScreenX, double cursorScreenY, boolean uiFocused, boolean shiftHeld) {
        if (uiFocused) {
            endIfDragging();
            resetState();
            wasMousePressed = false;
            return;
        }

        if (mousePressed && !wasMousePressed) {
            pressedOverStartBox = isCursorOverStartBox(rayOrigin, rayDirection);
            pressScreenX = cursorScreenX;
            pressScreenY = cursorScreenY;
            engaged = false;
            dragState = null;
        }

        if (mousePressed && pressedOverStartBox) {
            if (!engaged && TapThreshold.exceeded(pressScreenX, pressScreenY, cursorScreenX, cursorScreenY)) {
                engaged = true;
                tryStartDrag(rayOrigin, rayDirection, shiftHeld);
            }
            if (engaged && dragState != null) {
                updateDrag(rayOrigin, rayDirection);
            }
        }

        if (!mousePressed && wasMousePressed) {
            if (pressedOverStartBox && !engaged && onStartBoxTap != null) {
                onStartBoxTap.run();
            }
            endIfDragging();
            resetState();
        }

        wasMousePressed = mousePressed;
    }

    private void endIfDragging() {
        if (engaged && dragState != null) {
            handler.onEnd(dragState.rigid);
        }
    }

    private void resetState() {
        pressedOverStartBox = false;
        engaged = false;
        dragState = null;
    }

    public boolean isDragging() {
        return engaged && dragState != null;
    }

    /** True if rayOrigin+rayDirection currently intersects the start box. Used to swallow left-click presses before MC sees them. */
    public boolean isCursorOverStartBox(Vec3dCore rayOrigin, Vec3dCore rayDirection) {
        AABB first = boxController.getFirst();
        if (first == null) return false;
        return rayHitsAabb(rayOrigin, rayDirection, expand(first));
    }

    private void tryStartDrag(Vec3dCore origin, Vec3dCore direction, boolean rigid) {
        AABB first = boxController.getFirst();
        if (first == null) return;
        if (!rayHitsAabb(origin, direction, expand(first))) return;

        Vec3dCore cursorOnPlane = projectCursorToPlane(origin, direction, first.min.y);
        if (cursorOnPlane == null) return;

        double centerX = (first.min.x + first.max.x) * 0.5;
        double centerZ = (first.min.z + first.max.z) * 0.5;
        dragState = new DragState(
                first.min.y,
                centerX, centerZ,
                cursorOnPlane.x, cursorOnPlane.z,
                new Vec3dCore(centerX, first.min.y, centerZ),
                rigid
        );
        handler.onBegin(rigid);
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
        handler.onMove(newPos, dragState.rigid);
        dragState.lastEmittedPos = newPos;
    }

    private static AABB expand(AABB box) {
        return new AABB(
                new Vec3dCore(box.min.x - BoxDragController.EPS, box.min.y - BoxDragController.EPS, box.min.z - BoxDragController.EPS),
                new Vec3dCore(box.max.x + BoxDragController.EPS, box.max.y + BoxDragController.EPS, box.max.z + BoxDragController.EPS)
        );
    }

    private static Vec3dCore projectCursorToPlane(Vec3dCore origin, Vec3dCore direction, double planeY) {
        if (Math.abs(direction.y) < 1.0e-6) return null;
        double t = (planeY - origin.y) / direction.y;
        if (t < 0) return null;
        return new Vec3dCore(origin.x + direction.x * t, origin.y + direction.y * t, origin.z + direction.z * t);
    }

    private static boolean rayHitsAabb(Vec3dCore o, Vec3dCore d, AABB box) {
        double tmin = 0.0;
        double tmax = BoxDragController.PICK_REACH;

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
        final boolean rigid;
        Vec3dCore lastEmittedPos;

        DragState(double planeY, double startBoxX, double startBoxZ, double startCursorX, double startCursorZ, Vec3dCore initialBoxPos, boolean rigid) {
            this.planeY = planeY;
            this.startBoxX = startBoxX;
            this.startBoxZ = startBoxZ;
            this.startCursorX = startCursorX;
            this.startCursorZ = startCursorZ;
            this.rigid = rigid;
            this.lastEmittedPos = initialBoxPos;
        }
    }
}
