package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.TickState;

import java.util.ArrayList;
import java.util.List;

/**
 * Loader-agnostic owner of the simulated-path boxes. Loaders populate via add()
 * after each runSimulation, then drive draws by calling render(BoxRenderer, BoxColorPicker)
 * from their world-render hook. Drag pick logic lives in BoxDragController.
 */
public final class BoxController {

    private final List<AABB> boxes = new ArrayList<AABB>();
    private final List<TickState> states = new ArrayList<TickState>();

    public void add(TickState state) {
        boxes.add(AABB.ofCube(state.position, BoxStyle.BOX_SIZE));
        states.add(state);
    }

    public void clearAll() {
        boxes.clear();
        states.clear();
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

    public void render(BoxRenderer renderer, BoxColorPicker picker) {
        for (int i = 0; i < boxes.size(); i++) {
            renderer.drawBox(boxes.get(i), picker.argbFor(i, states.get(i)));
        }
    }
}
