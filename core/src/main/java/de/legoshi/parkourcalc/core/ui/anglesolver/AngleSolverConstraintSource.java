package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.render.ConstraintBoxSource;
import de.legoshi.parkourcalc.core.render.ConstraintPlate;
import de.legoshi.parkourcalc.core.render.ConstraintShapes;
import de.legoshi.parkourcalc.core.render.ConstraintStyle;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.ConstraintSelection;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the angle-solver's per-tick constraints into world plates for the overlay (gh-145), bridging
 * {@link AngleSolverState} to the loader-agnostic {@link ConstraintBoxSource} port. Each constraint is
 * anchored at its tick's simulated foot position (the {@link BoxController} index equals the absolute
 * tick index the constraint is keyed by), so the constraint->tick mapping is explicit.
 *
 * <p>Only enabled, spatial (X/Z) constraints produce geometry; facing/velocity constraints have no
 * world extent and are skipped (see {@link ConstraintShapes}). A co-tick bounded X range and Z range
 * merge into a single landing pad; lone bounded ranges, equalities, and open-ended comparisons each
 * become their own plate. Each plate carries the indices (into the tick's constraint list) it came from
 * and whether it is highlighted by the current selection. Plates render only while the solver view is
 * active.
 */
public final class AngleSolverConstraintSource implements ConstraintBoxSource {

    private final AngleSolverState state;
    private final BoxController boxController;
    private final ActiveGate active;
    private final Settings settings;
    private final SelectionManager selection;
    private final ConstraintSelection constraintSelection;

    /** Lets the overlay vanish when the Angle Solver view is closed. */
    public interface ActiveGate {
        boolean isActive();
    }

    public AngleSolverConstraintSource(AngleSolverState state, BoxController boxController, ActiveGate active,
                                       Settings settings, SelectionManager selection, ConstraintSelection constraintSelection) {
        this.state = state;
        this.boxController = boxController;
        this.active = active;
        this.settings = settings;
        this.selection = selection;
        this.constraintSelection = constraintSelection;
    }

    @Override
    public List<ConstraintPlate> platesAt(int tickIndex) {
        if (!active.isActive()) return java.util.Collections.emptyList();
        TickConstraints tc = state.tickConstraintsOrNull(tickIndex);
        if (tc == null || tc.getConstraints().isEmpty()) return java.util.Collections.emptyList();
        Vec3dCore foot = boxController.getPosition(tickIndex);
        if (foot == null) return java.util.Collections.emptyList();

        List<Constraint> all = tc.getConstraints();
        List<Integer> drawable = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Constraint c = all.get(i);
            if (c.isEnabled() && ConstraintShapes.isDrawable(c)) drawable.add(i);
        }
        if (drawable.isEmpty()) return java.util.Collections.emptyList();

        ConstraintStyle style = BoxStyle.constraintStyle(settings);
        int xIdx = firstBoundedRangeIndex(all, drawable, Constraint.Field.X);
        int zIdx = firstBoundedRangeIndex(all, drawable, Constraint.Field.Z);
        boolean merged = xIdx >= 0 && zIdx >= 0;

        List<ConstraintPlate> out = new ArrayList<>();
        if (merged) {
            out.add(tagged(ConstraintShapes.pad(all.get(xIdx), all.get(zIdx), foot, style, tickIndex, new int[]{xIdx, zIdx})));
        }
        for (int idx : drawable) {
            if (merged && (idx == xIdx || idx == zIdx)) continue;
            Constraint c = all.get(idx);
            int[] one = {idx};
            ConstraintPlate plate;
            if (ConstraintShapes.sense(c.getOp()) == ConstraintShapes.Sense.EXCLUDE) {
                plate = ConstraintShapes.exclude(c, foot, style, tickIndex, one);
            } else if (c.isRange()) {
                plate = ConstraintShapes.strip(c, foot, style, tickIndex, one);
            } else {
                plate = ConstraintShapes.plane(c, foot, style, tickIndex, one);
            }
            out.add(tagged(plate));
        }
        return out;
    }

    private ConstraintPlate tagged(ConstraintPlate plate) {
        for (int idx : plate.constraintIndices) {
            if (constraintSelection.highlights(plate.tick, idx, selection)) {
                plate.highlighted = true;
                break;
            }
        }
        return plate;
    }

    private static int firstBoundedRangeIndex(List<Constraint> all, List<Integer> drawable, Constraint.Field field) {
        for (int idx : drawable) {
            Constraint c = all.get(idx);
            if (c.isRange() && c.getField() == field) return idx;
        }
        return -1;
    }

    /**
     * Content stamp over the drawable constraints, their anchor-tick selection, and the focused
     * constraint. Folded into the cached geometry's structural hash so editing a constraint, or
     * selecting/focusing a constrained tick (which recolours the plate), rebuilds the buffers even
     * though the path positions are unchanged. Selecting a tick with no constraints does not change it,
     * so the cheaper in-place box-selection patch still applies there.
     */
    @Override
    public long revision() {
        if (!active.isActive()) return 0L;
        long h = 1L;
        for (Integer tickKey : state.populatedTicks()) {
            TickConstraints tc = state.tickConstraintsOrNull(tickKey);
            if (tc == null) continue;
            boolean anyDrawable = false;
            for (Constraint c : tc.getConstraints()) {
                if (!c.isEnabled() || !ConstraintShapes.isDrawable(c)) continue;
                anyDrawable = true;
                h = 31 * h + tickKey;
                h = 31 * h + c.getField().ordinal();
                h = 31 * h + c.getOp().ordinal();
                h = 31 * h + Double.hashCode(c.getValue());
                h = 31 * h + Double.hashCode(c.getLo());
                h = 31 * h + Double.hashCode(c.getHi());
                h = 31 * h + (c.isLoInclusive() ? 1 : 0);
                h = 31 * h + (c.isHiInclusive() ? 1 : 0);
            }
            if (anyDrawable) h = 31 * h + (selection.isSelected(tickKey + 1) ? 1 : 0);
        }
        h = 31 * h + Long.hashCode(constraintSelection.revision());
        return h;
    }
}
