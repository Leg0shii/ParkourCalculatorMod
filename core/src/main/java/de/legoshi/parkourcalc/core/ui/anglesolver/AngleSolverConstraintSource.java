package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
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
 * world extent and are skipped (see {@link ConstraintShapes}). A relative X/Z constraint resolves to
 * absolute bounds against its reference tick's simulated position before shaping, so its plate floats
 * with the current path (and vanishes while the reference tick has no simulated position); the
 * geometry cache keys on the path revision, so a resim re-resolves it. A co-tick bounded X range and Z range
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
        Constraint[] resolved = new Constraint[all.size()];
        for (int i = 0; i < all.size(); i++) {
            Constraint c = all.get(i);
            if (!c.isEnabled() || !ConstraintShapes.isDrawable(c)) continue;
            Constraint r = resolveRelative(c);
            if (r == null) continue;
            resolved[i] = r;
            drawable.add(i);
        }
        if (drawable.isEmpty()) return java.util.Collections.emptyList();

        ConstraintStyle style = BoxStyle.constraintStyle(settings);
        int xIdx = firstBoundedRangeIndex(all, drawable, Constraint.Field.X);
        int zIdx = firstBoundedRangeIndex(all, drawable, Constraint.Field.Z);
        boolean merged = xIdx >= 0 && zIdx >= 0;

        boolean solverUnmet = solverUnmetTicks().contains(tickIndex);
        List<ConstraintPlate> out = new ArrayList<>();
        if (merged) {
            out.add(tagged(ConstraintShapes.pad(resolved[xIdx], resolved[zIdx], foot, style, tickIndex, new int[]{xIdx, zIdx}), solverUnmet));
        }
        for (int idx : drawable) {
            if (merged && (idx == xIdx || idx == zIdx)) continue;
            Constraint c = resolved[idx];
            int[] one = {idx};
            ConstraintPlate plate;
            if (ConstraintShapes.sense(c.getOp()) == ConstraintShapes.Sense.EXCLUDE) {
                plate = ConstraintShapes.exclude(c, foot, style, tickIndex, one);
            } else if (c.isRange()) {
                plate = ConstraintShapes.strip(c, foot, style, tickIndex, one);
            } else {
                plate = ConstraintShapes.plane(c, foot, style, tickIndex, one);
            }
            out.add(tagged(plate, solverUnmet));
        }
        return out;
    }

    private java.util.Set<Integer> solverUnmetTicks() {
        SolveResult r = state.getResult();
        return r == null ? java.util.Collections.emptySet() : r.getUnmetTicks();
    }

    private Constraint resolveRelative(Constraint c) {
        if (!c.isRelative()) return c;
        Vec3dCore ref = boxController.getPosition(c.getRefTick());
        if (ref == null) return null;
        double base = c.getField() == Constraint.Field.X ? ref.x : ref.z;
        Constraint r = c.copy();
        r.setRefTick(null);
        r.setValue(r.getValue() + base);
        r.setLo(r.getLo() + base);
        r.setHi(r.getHi() + base);
        return r;
    }

    private ConstraintPlate tagged(ConstraintPlate plate, boolean solverUnmet) {
        if (solverUnmet && plate.satisfied) {
            ConstraintPlate unmet = new ConstraintPlate(plate.sense, false, plate.front, plate.back,
                    plate.tick, plate.constraintIndices, plate.pickable);
            unmet.highlighted = plate.highlighted;
            plate = unmet;
        }
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
                h = 31 * h + (c.getRefTick() == null ? -1 : c.getRefTick());
            }
            if (anyDrawable) h = 31 * h + (selection.isSelected(tickKey + 1) ? 1 : 0);
        }
        h = 31 * h + Long.hashCode(constraintSelection.revision());
        h = 31 * h + solverUnmetTicks().hashCode();
        return h;
    }
}
