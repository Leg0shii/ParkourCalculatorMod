package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.PathVertexLayout;
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

    private static final double PICK_REACH = 128.0;
    private static final double PICK_EPS = 1.0e-3;

    private static final double ARROW_SHAFT_LEN = 0.29;
    private static final double ARROW_HEAD_LEN = 0.12;
    private static final double ARROW_HEAD_HALF_WIDTH = 0.065;
    private static final double ARROW_THICKNESS = 0.016;

    private static final int GIZMO_SEGMENTS = 48;

    private final List<Vec3dCore> positions = new ArrayList<>();
    private final List<TickState> states = new ArrayList<>();
    private final List<AABB> tickAabbs = new ArrayList<>();

    private double boxSize = BoxStyle.BOX_SIZE;
    private long geometryRev = 1;

    public void add(TickState state) {
        positions.add(state.position);
        states.add(state);
        tickAabbs.add(AABB.ofCube(state.position, boxSize));
        geometryRev++;
    }

    public void clearAll() {
        positions.clear();
        states.clear();
        tickAabbs.clear();
        geometryRev++;
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }

    public int size() {
        return positions.size();
    }

    public void setBoxSize(double size) {
        if (size == this.boxSize) return;
        this.boxSize = size;
        tickAabbs.clear();
        for (Vec3dCore position : positions) {
            tickAabbs.add(AABB.ofCube(position, size));
        }
        geometryRev++;
    }

    /** Monotonic counter; loaders snapshot it and rebuild cached buffers when it changes. */
    public long getGeometryRev() {
        return geometryRev;
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

    public void render(BoxRenderer renderer, BoxColorPicker picker,
                       double camX, double camY, double camZ, double maxDistanceSq) {
        for (int i = 0; i < positions.size(); i++) {
            if (!inRange(i, camX, camY, camZ, maxDistanceSq)) continue;
            renderer.drawBox(tickAabbs.get(i), picker.argbFor(i, states.get(i)));
        }
    }

    /** Cached AABB at index i, in the simulator's world coords. Null if out of range. */
    public AABB getTickAabb(int index) {
        if (index < 0 || index >= tickAabbs.size()) return null;
        return tickAabbs.get(index);
    }

    public Vec3dCore getPosition(int index) {
        if (index < 0 || index >= positions.size()) return null;
        return positions.get(index);
    }

    public TickState getState(int index) {
        if (index < 0 || index >= states.size()) return null;
        return states.get(index);
    }

    public void renderHitboxFloorOutline(BoxRenderer renderer, BoxColorPicker picker, boolean useSubtickPositions,
                                         double camX, double camY, double camZ, double maxDistanceSq) {
        for (int i = 0; i < states.size(); i++) {
            if (!inRange(i, camX, camY, camZ, maxDistanceSq)) continue;
            emitHitboxFloorOutlineAt(renderer, picker.argbFor(i, states.get(i)), useSubtickPositions, i);
        }
    }

    /** Emits one tick's hitbox floor outline; used both by the full-pass loop and by selection patching. */
    public void emitHitboxFloorOutlineAt(BoxRenderer renderer, int argb, boolean useSubtickPositions, int i) {
        double t = BoxStyle.HITBOX_EDGE_THICKNESS;
        List<Vec3dCore> walk = walkFor(i, states.get(i), useSubtickPositions);
        for (int k = 0; k < walk.size(); k++) {
            Vec3dCore p = walk.get(k);
            double x0 = p.x - BoxStyle.HITBOX_HALF_WIDTH;
            double z0 = p.z - BoxStyle.HITBOX_HALF_WIDTH;
            double x1 = p.x + BoxStyle.HITBOX_HALF_WIDTH;
            double z1 = p.z + BoxStyle.HITBOX_HALF_WIDTH;
            double y = p.y;
            emitThickEdge(renderer, x0, y, z0, x1, y, z0, t, argb);
            emitThickEdge(renderer, x0, y, z1, x1, y, z1, t, argb);
            emitThickEdge(renderer, x0, y, z0, x0, y, z1, t, argb);
            emitThickEdge(renderer, x1, y, z0, x1, y, z1, t, argb);
        }
    }

    /** Number of walk positions (sub-tick samples) for this tick; hitbox geometry scales by this. */
    public int hitboxWalkCount(int index, boolean useSubtickPositions) {
        return walkFor(index, states.get(index), useSubtickPositions).size();
    }

    private long totalHitboxWalk(boolean useSubtickPositions) {
        long sum = 0;
        for (int i = 0; i < states.size(); i++) {
            sum += hitboxWalkCount(i, useSubtickPositions);
        }
        return sum;
    }

    /** Whether the faces pass fits one buffer; loaders drop the hitbox (not the path) when it doesn't. */
    public boolean facePassFitsBudget(int hitboxEdges, boolean useSubtickPositions, boolean showArrows) {
        if (hitboxEdges == 0) return true;
        long n = positions.size();
        long faceVerts = n * PathVertexLayout.FACE_VERTS_PER_BOX
                + (long) hitboxEdges * PathVertexLayout.THICK_EDGE_VERTS * totalHitboxWalk(useSubtickPositions)
                + (showArrows && n > 0 ? (n - 1) * PathVertexLayout.ARROW_VERTS_PER_BOX : 0);
        return faceVerts <= PathVertexLayout.MAX_PASS_VERTICES;
    }

    public void renderHitboxFullWireframe(BoxRenderer renderer, BoxColorPicker picker, boolean useSubtickPositions,
                                          double camX, double camY, double camZ, double maxDistanceSq) {
        for (int i = 0; i < states.size(); i++) {
            if (!inRange(i, camX, camY, camZ, maxDistanceSq)) continue;
            emitHitboxFullWireframeAt(renderer, picker.argbFor(i, states.get(i)), useSubtickPositions, i);
        }
    }

    /** Emits one tick's full hitbox wireframe; used both by the full-pass loop and by selection patching. */
    public void emitHitboxFullWireframeAt(BoxRenderer renderer, int argb, boolean useSubtickPositions, int i) {
        double t = BoxStyle.HITBOX_EDGE_THICKNESS;
        TickState s = states.get(i);
        List<Vec3dCore> walk = walkFor(i, s, useSubtickPositions);
        for (int k = 0; k < walk.size(); k++) {
            AABB hb = BoxStyle.hitboxAabbAt(walk.get(k), s.sneaking);
            double x0 = hb.min.x, y0 = hb.min.y, z0 = hb.min.z;
            double x1 = hb.max.x, y1 = hb.max.y, z1 = hb.max.z;
            emitThickEdge(renderer, x0, y0, z0, x1, y0, z0, t, argb);
            emitThickEdge(renderer, x0, y0, z1, x1, y0, z1, t, argb);
            emitThickEdge(renderer, x0, y0, z0, x0, y0, z1, t, argb);
            emitThickEdge(renderer, x1, y0, z0, x1, y0, z1, t, argb);
            emitThickEdge(renderer, x0, y1, z0, x1, y1, z0, t, argb);
            emitThickEdge(renderer, x0, y1, z1, x1, y1, z1, t, argb);
            emitThickEdge(renderer, x0, y1, z0, x0, y1, z1, t, argb);
            emitThickEdge(renderer, x1, y1, z0, x1, y1, z1, t, argb);
            emitThickEdge(renderer, x0, y0, z0, x0, y1, z0, t, argb);
            emitThickEdge(renderer, x1, y0, z0, x1, y1, z0, t, argb);
            emitThickEdge(renderer, x0, y0, z1, x0, y1, z1, t, argb);
            emitThickEdge(renderer, x1, y0, z1, x1, y1, z1, t, argb);
        }
    }

    private static void emitThickEdge(BoxRenderer renderer, double x0, double y0, double z0, double x1, double y1, double z1, double thickness, int argb) {
        double h = thickness * 0.5;
        double minX = Math.min(x0, x1) - h, maxX = Math.max(x0, x1) + h;
        double minY = Math.min(y0, y1) - h, maxY = Math.max(y0, y1) + h;
        double minZ = Math.min(z0, z1) - h, maxZ = Math.max(z0, z1) + h;
        renderer.drawBox(new AABB(new Vec3dCore(minX, minY, minZ), new Vec3dCore(maxX, maxY, maxZ)), argb);
    }

    /** Contiguous in-range tick runs as flattened [start0,end0,start1,end1,...]; {0,size} when maxSq is infinite. */
    public int[] inRangeRuns(double camX, double camY, double camZ, double maxSq) {
        int n = positions.size();
        if (maxSq == Double.POSITIVE_INFINITY) {
            return new int[]{0, n};
        }
        List<Integer> bounds = new ArrayList<Integer>();
        boolean inRun = false;
        for (int i = 0; i < n; i++) {
            boolean in = inRange(i, camX, camY, camZ, maxSq);
            if (in && !inRun) {
                bounds.add(i);
                inRun = true;
            } else if (!in && inRun) {
                bounds.add(i);
                inRun = false;
            }
        }
        if (inRun) bounds.add(n);
        int[] runs = new int[bounds.size()];
        for (int i = 0; i < runs.length; i++) {
            runs[i] = bounds.get(i);
        }
        return runs;
    }

    private boolean inRange(int i, double camX, double camY, double camZ, double maxSq) {
        if (maxSq == Double.POSITIVE_INFINITY) return true;
        Vec3dCore p = positions.get(i);
        double dx = p.x - camX;
        double dy = p.y - camY;
        double dz = p.z - camZ;
        return dx * dx + dy * dy + dz * dz <= maxSq;
    }

    private List<Vec3dCore> walkFor(int tickIndex, TickState s, boolean useSubtickPositions) {
        if (useSubtickPositions && !s.subtickPath.isEmpty()) {
            return s.subtickPath;
        }
        return Collections.singletonList(positions.get(tickIndex));
    }

    public void renderYawArrows(BoxRenderer renderer, int argb, double camX, double camY, double camZ, double maxDistanceSq) {
        if (positions.isEmpty()) return;
        double half = boxSize * 0.5;
        // Arrow at box i is the outgoing facing: states[i+1].yaw is the look direction used
        // during the tick that leaves box i. The final box has no outgoing tick, so it gets no arrow.
        for (int i = 0; i + 1 < states.size(); i++) {
            if (!inRange(i, camX, camY, camZ, maxDistanceSq)) continue;
            Vec3dCore p = positions.get(i);
            double cx = p.x + half;
            double cy = p.y + half;
            double cz = p.z + half;

            double yawRad = Math.toRadians(states.get(i + 1).yaw);
            double fx = -Math.sin(yawRad);
            double fz = Math.cos(yawRad);

            double tipX = cx + fx * ARROW_SHAFT_LEN;
            double tipZ = cz + fz * ARROW_SHAFT_LEN;
            double baseX = tipX - fx * ARROW_HEAD_LEN;
            double baseZ = tipZ - fz * ARROW_HEAD_LEN;

            // Shaft: oriented thin box from box center to the base of the head.
            double perpShaftX = -fz * (ARROW_THICKNESS * 0.5);
            double perpShaftZ = fx * (ARROW_THICKNESS * 0.5);
            emitOrientedShaft(renderer, cx, cy, cz, baseX, baseZ, perpShaftX, perpShaftZ, ARROW_THICKNESS, argb);

            // Head: filled triangle, drawn as two coincident triangles for a slight Y extent so it reads from above/below.
            double perpHeadX = -fz * ARROW_HEAD_HALF_WIDTH;
            double perpHeadZ = fx * ARROW_HEAD_HALF_WIDTH;
            double c1x = baseX + perpHeadX, c1z = baseZ + perpHeadZ;
            double c2x = baseX - perpHeadX, c2z = baseZ - perpHeadZ;
            double headLow = cy - ARROW_THICKNESS * 0.5;
            double headHigh = cy + ARROW_THICKNESS * 0.5;
            renderer.drawTriangle(tipX, headLow, tipZ, c1x, headLow, c1z, c2x, headLow, c2z, argb);
            renderer.drawTriangle(tipX, headHigh, tipZ, c2x, headHigh, c2z, c1x, headHigh, c1z, argb);
            // Side walls so the head has thickness when viewed edge-on.
            renderer.drawTriangle(tipX, headLow, tipZ, c1x, headHigh, c1z, tipX, headHigh, tipZ, argb);
            renderer.drawTriangle(tipX, headLow, tipZ, c1x, headLow, c1z, c1x, headHigh, c1z, argb);
            renderer.drawTriangle(tipX, headLow, tipZ, tipX, headHigh, tipZ, c2x, headHigh, c2z, argb);
            renderer.drawTriangle(tipX, headLow, tipZ, c2x, headHigh, c2z, c2x, headLow, c2z, argb);
            renderer.drawTriangle(c1x, headLow, c1z, c2x, headHigh, c2z, c1x, headHigh, c1z, argb);
            renderer.drawTriangle(c1x, headLow, c1z, c2x, headLow, c2z, c2x, headHigh, c2z, argb);
        }
    }

    private static void emitOrientedShaft(BoxRenderer renderer, double sx, double sy, double sz, double ex, double ez, double perpX, double perpZ, double thickness, int argb) {
        double h = thickness * 0.5;
        double yLow = sy - h, yHigh = sy + h;
        double p0x = sx - perpX, p0z = sz - perpZ;
        double p1x = sx + perpX, p1z = sz + perpZ;
        double p2x = ex + perpX, p2z = ez + perpZ;
        double p3x = ex - perpX, p3z = ez - perpZ;
        renderer.drawTriangle(p0x, yLow, p0z, p1x, yLow, p1z, p2x, yLow, p2z, argb);
        renderer.drawTriangle(p0x, yLow, p0z, p2x, yLow, p2z, p3x, yLow, p3z, argb);
        renderer.drawTriangle(p0x, yHigh, p0z, p2x, yHigh, p2z, p1x, yHigh, p1z, argb);
        renderer.drawTriangle(p0x, yHigh, p0z, p3x, yHigh, p3z, p2x, yHigh, p2z, argb);
        renderer.drawTriangle(p0x, yLow, p0z, p1x, yHigh, p1z, p0x, yHigh, p0z, argb);
        renderer.drawTriangle(p0x, yLow, p0z, p1x, yLow, p1z, p1x, yHigh, p1z, argb);
        renderer.drawTriangle(p1x, yLow, p1z, p2x, yHigh, p2z, p1x, yHigh, p1z, argb);
        renderer.drawTriangle(p1x, yLow, p1z, p2x, yLow, p2z, p2x, yHigh, p2z, argb);
        renderer.drawTriangle(p2x, yLow, p2z, p3x, yHigh, p3z, p2x, yHigh, p2z, argb);
        renderer.drawTriangle(p2x, yLow, p2z, p3x, yLow, p3z, p3x, yHigh, p3z, argb);
        renderer.drawTriangle(p3x, yLow, p3z, p0x, yHigh, p0z, p3x, yHigh, p3z, argb);
        renderer.drawTriangle(p3x, yLow, p3z, p0x, yLow, p0z, p0x, yHigh, p0z, argb);
    }

    public void renderYawGizmo(BoxRenderer renderer, Vec3dCore center, double yawDegrees, double radius, int circleArgb, int directionArgb) {
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

    /** Per-box subtick vertex offsets, mirroring renderPath's emission exactly; starts[n] is the total. */
    public int[] subtickVertexStarts() {
        int n = states.size();
        int[] starts = new int[n + 1];
        if (n < 2) return starts;
        int acc = 0;
        Vec3dCore prev = null;
        for (int i = 0; i < n; i++) {
            starts[i] = acc;
            List<Vec3dCore> path = states.get(i).subtickPath;
            if (path == null || path.isEmpty()) {
                Vec3dCore cur = positions.get(i);
                if (prev != null && !prev.equals(cur)) acc += 2;
                prev = cur;
            } else {
                for (Vec3dCore cur : path) {
                    if (prev != null && !prev.equals(cur)) acc += 2;
                    prev = cur;
                }
            }
        }
        starts[n] = acc;
        return starts;
    }

    public void renderPath(BoxRenderer renderer, int argb, double camX, double camY, double camZ, double maxDistanceSq) {
        if (states.size() < 2) return;
        double half = boxSize * 0.5;
        Vec3dCore prev = null;
        for (int i = 0; i < states.size(); i++) {
            if (!inRange(i, camX, camY, camZ, maxDistanceSq)) {
                prev = null;
                continue;
            }
            List<Vec3dCore> path = states.get(i).subtickPath;
            if (path == null || path.isEmpty()) {
                Vec3dCore cur = positions.get(i);
                if (prev != null && !prev.equals(cur)) {
                    renderer.drawLine(
                            prev.x + half, prev.y + half, prev.z + half,
                            cur.x + half, cur.y + half, cur.z + half,
                            argb);
                }
                prev = cur;
            } else {
                for (Vec3dCore cur : path) {
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
}
