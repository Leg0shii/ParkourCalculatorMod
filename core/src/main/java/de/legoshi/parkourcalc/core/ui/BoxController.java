package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loader-agnostic owner of the simulated-path boxes. Loaders populate via add()
 * after each runSimulation, then drive draws by calling render(BoxRenderer, BoxColorPicker)
 * from their world-render hook. Drag pick logic lives in BoxDragController.
 */
public final class BoxController {

    private final List<Vec3dCore> positions = new ArrayList<Vec3dCore>();
    private final List<TickState> states = new ArrayList<TickState>();

    private double boxSize = BoxStyle.BOX_SIZE;

    public void add(TickState state) {
        positions.add(state.position);
        states.add(state);
    }

    public void clearAll() {
        positions.clear();
        states.clear();
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }

    public int size() {
        return positions.size();
    }

    public void setBoxSize(double size) {
        this.boxSize = size;
    }

    /** First box's AABB, or null if empty. Used loader-side for start-position drag. */
    public AABB getFirst() {
        return positions.isEmpty() ? null : AABB.ofCube(positions.get(0), boxSize);
    }

    public AABB getBox(int index) {
        if (index < 0 || index >= positions.size()) return null;
        return AABB.ofCube(positions.get(index), boxSize);
    }

    public Vec3dCore getCenter(int index) {
        if (index < 0 || index >= positions.size()) return null;
        Vec3dCore p = positions.get(index);
        double half = boxSize * 0.5;
        return new Vec3dCore(p.x + half, p.y + half, p.z + half);
    }

    /** Yaw recorded on the simulator/InputRow at this index. */
    public float getYaw(int index) {
        return states.get(index).yaw;
    }

    /** Unmodifiable view of recorded per-tick states; index aligned with positions. */
    public List<TickState> getStates() {
        return Collections.unmodifiableList(states);
    }

    /** Returns the index of the closest box hit by the ray, or -1 if none. */
    public int pickBoxIndex(Vec3dCore rayOrigin, Vec3dCore rayDirection) {
        int best = -1;
        double bestT = Double.POSITIVE_INFINITY;
        for (int i = 0; i < positions.size(); i++) {
            Vec3dCore p = positions.get(i);
            AABB box = new AABB(
                    new Vec3dCore(p.x - PICK_EPS, p.y - PICK_EPS, p.z - PICK_EPS),
                    new Vec3dCore(p.x + boxSize + PICK_EPS, p.y + boxSize + PICK_EPS, p.z + boxSize + PICK_EPS)
            );
            double t = rayHitT(rayOrigin, rayDirection, box, PICK_REACH);
            if (t >= 0 && t < bestT) {
                bestT = t;
                best = i;
            }
        }
        return best;
    }

    private static final double PICK_REACH = 128.0;
    private static final double PICK_EPS = 1.0e-3;

    private static double rayHitT(Vec3dCore o, Vec3dCore d, AABB box, double maxT) {
        double tmin = 0.0;
        double tmax = maxT;

        double[] r = slab(o.x, d.x, box.min.x, box.max.x);
        if (r == null) return -1;
        tmin = Math.max(tmin, r[0]);
        tmax = Math.min(tmax, r[1]);
        if (tmax < tmin) return -1;

        r = slab(o.y, d.y, box.min.y, box.max.y);
        if (r == null) return -1;
        tmin = Math.max(tmin, r[0]);
        tmax = Math.min(tmax, r[1]);
        if (tmax < tmin) return -1;

        r = slab(o.z, d.z, box.min.z, box.max.z);
        if (r == null) return -1;
        tmin = Math.max(tmin, r[0]);
        tmax = Math.min(tmax, r[1]);
        if (tmax < tmin) return -1;

        return tmin;
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

    public void render(BoxRenderer renderer, BoxColorPicker picker) {
        for (int i = 0; i < positions.size(); i++) {
            AABB box = AABB.ofCube(positions.get(i), boxSize);
            renderer.drawBox(box, picker.argbFor(i, states.get(i)));
        }
    }

    public void renderHitboxFloorOutline(BoxRenderer renderer, BoxColorPicker picker, boolean useSubtickPositions) {
        for (int i = 0; i < states.size(); i++) {
            TickState s = states.get(i);
            int argb = picker.argbFor(i, s);
            List<Vec3dCore> walk = walkFor(i, s, useSubtickPositions);
            for (int k = 0; k < walk.size(); k++) {
                Vec3dCore p = walk.get(k);
                double x0 = p.x - BoxStyle.HITBOX_HALF_WIDTH;
                double z0 = p.z - BoxStyle.HITBOX_HALF_WIDTH;
                double x1 = p.x + BoxStyle.HITBOX_HALF_WIDTH;
                double z1 = p.z + BoxStyle.HITBOX_HALF_WIDTH;
                double y = p.y;
                renderer.drawLine(x0, y, z0, x1, y, z0, argb);
                renderer.drawLine(x1, y, z0, x1, y, z1, argb);
                renderer.drawLine(x1, y, z1, x0, y, z1, argb);
                renderer.drawLine(x0, y, z1, x0, y, z0, argb);
            }
        }
    }

    public void renderHitboxFullWireframe(BoxRenderer renderer, BoxColorPicker picker, boolean useSubtickPositions) {
        for (int i = 0; i < states.size(); i++) {
            TickState s = states.get(i);
            int argb = picker.argbFor(i, s);
            List<Vec3dCore> walk = walkFor(i, s, useSubtickPositions);
            for (int k = 0; k < walk.size(); k++) {
                renderer.drawBox(BoxStyle.hitboxAabbAt(walk.get(k), s.sneaking), argb);
            }
        }
    }

    private List<Vec3dCore> walkFor(int tickIndex, TickState s, boolean useSubtickPositions) {
        if (useSubtickPositions && s.subtickPath != null && !s.subtickPath.isEmpty()) {
            return s.subtickPath;
        }
        return Collections.singletonList(positions.get(tickIndex));
    }

    public void renderYawArrows(BoxRenderer renderer, int argb) {
        if (positions.isEmpty()) return;
        double half = boxSize * 0.5;
        for (int i = 0; i < states.size(); i++) {
            TickState s = states.get(i);
            Vec3dCore p = positions.get(i);
            double cx = p.x + half;
            double cy = p.y + half;
            double cz = p.z + half;

            double yawRad = Math.toRadians(s.yaw);
            double fx = -Math.sin(yawRad);
            double fz = Math.cos(yawRad);

            double tipX = cx + fx * ARROW_SHAFT_LEN;
            double tipZ = cz + fz * ARROW_SHAFT_LEN;
            renderer.drawLine(cx, cy, cz, tipX, cy, tipZ, argb);

            double baseX = tipX - fx * ARROW_HEAD_LEN;
            double baseZ = tipZ - fz * ARROW_HEAD_LEN;
            double perpX = -fz * ARROW_HEAD_HALF_WIDTH;
            double perpZ = fx * ARROW_HEAD_HALF_WIDTH;
            renderer.drawLine(tipX, cy, tipZ, baseX + perpX, cy, baseZ + perpZ, argb);
            renderer.drawLine(tipX, cy, tipZ, baseX - perpX, cy, baseZ - perpZ, argb);
        }
    }

    private static final double ARROW_SHAFT_LEN = 0.45;
    private static final double ARROW_HEAD_LEN = 0.15;
    private static final double ARROW_HEAD_HALF_WIDTH = 0.08;

    public void renderYawGizmo(BoxRenderer renderer, Vec3dCore center, double yawDegrees, double radius,
                                int circleArgb, int directionArgb) {
        double yawRad = Math.toRadians(yawDegrees);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);

        double prevX = center.x + radius;
        double prevZ = center.z;
        for (int i = 1; i <= GIZMO_SEGMENTS; i++) {
            double a = (i / (double) GIZMO_SEGMENTS) * Math.PI * 2.0;
            double nx = center.x + Math.cos(a) * radius;
            double nz = center.z + Math.sin(a) * radius;
            renderer.drawLine(prevX, center.y, prevZ, nx, center.y, nz, circleArgb);
            prevX = nx;
            prevZ = nz;
        }

        double tipX = center.x + fx * radius;
        double tipZ = center.z + fz * radius;
        renderer.drawLine(center.x, center.y, center.z, tipX, center.y, tipZ, directionArgb);

        double headLen = radius * 0.25;
        double headHalfWidth = radius * 0.15;
        double baseX = tipX - fx * headLen;
        double baseZ = tipZ - fz * headLen;
        double perpX = -fz * headHalfWidth;
        double perpZ = fx * headHalfWidth;
        renderer.drawLine(tipX, center.y, tipZ, baseX + perpX, center.y, baseZ + perpZ, directionArgb);
        renderer.drawLine(tipX, center.y, tipZ, baseX - perpX, center.y, baseZ - perpZ, directionArgb);
    }

    private static final int GIZMO_SEGMENTS = 48;

    public void renderPath(BoxRenderer renderer, int argb) {
        if (states.size() < 2) return;
        double half = boxSize * 0.5;
        Vec3dCore prev = null;
        for (int i = 0; i < states.size(); i++) {
            List<Vec3dCore> path = states.get(i).subtickPath;
            List<Vec3dCore> walk = (path == null || path.isEmpty())
                    ? Collections.singletonList(positions.get(i))
                    : path;
            for (int k = 0; k < walk.size(); k++) {
                Vec3dCore cur = walk.get(k);
                if (prev != null && !prev.equals(cur)) {
                    renderer.drawLine(
                            prev.x + half, prev.y + half, prev.z + half,
                            cur.x + half, cur.y + half, cur.z + half,
                            argb);
                }
                prev = cur;
            }
        }
    }
}
