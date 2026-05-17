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
