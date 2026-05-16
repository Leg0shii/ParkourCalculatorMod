package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;

/**
 * Loader-agnostic owner of the simulated-path boxes. Loaders populate via add()
 * after each runSimulation, then drive draws by calling render(BoxRenderer) from
 * their world-render hook. Drag pick logic lives in BoxDragController.
 */
public final class BoxController {

    private final List<AABB> boxes = new ArrayList<AABB>();

    public void add(Vec3dCore corner) {
        boxes.add(AABB.ofCube(corner, BoxStyle.BOX_SIZE));
    }

    public void clearAll() {
        boxes.clear();
    }

    public boolean isEmpty() {
        return boxes.isEmpty();
    }

    public int size() {
        return boxes.size();
    }

    /** First box's AABB, or null if empty. Used loader-side for start-position drag. */
    public AABB getFirst() {
        return boxes.isEmpty() ? null : boxes.get(0);
    }

    public void render(BoxRenderer renderer, int argb) {
        for (int i = 0; i < boxes.size(); i++) {
            renderer.drawBox(boxes.get(i), argb);
        }
    }
}
