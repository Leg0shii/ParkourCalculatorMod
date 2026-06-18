package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.render.ConstraintBoxSource;
import de.legoshi.parkourcalc.core.render.ConstraintShapes;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the angle-solver's per-tick constraints into world plates for the overlay (gh-145), bridging
 * {@link AngleSolverState} to the loader-agnostic {@link ConstraintBoxSource} port. Each constraint is
 * anchored at its tick's simulated foot position (the {@link BoxController} index equals the absolute
 * tick index the constraint is keyed by), so the constraint→tick mapping is explicit.
 *
 * <p>Only enabled, spatial (X/Z) constraints produce geometry; facing/velocity constraints have no
 * world extent and are skipped (see {@link ConstraintShapes}). Plates render only while the solver view
 * is active, so constraints don't clutter the world when the feature is closed.
 */
public final class AngleSolverConstraintSource implements ConstraintBoxSource {

    private final AngleSolverState state;
    private final BoxController boxController;
    private final ActiveGate active;

    /** Lets the overlay vanish when the Angle Solver view is closed, without coupling to Settings here. */
    public interface ActiveGate {
        boolean isActive();
    }

    public AngleSolverConstraintSource(AngleSolverState state, BoxController boxController, ActiveGate active) {
        this.state = state;
        this.boxController = boxController;
        this.active = active;
    }

    @Override
    public List<AABB> boxesAt(int tickIndex) {
        if (!active.isActive()) return java.util.Collections.emptyList();
        TickConstraints tc = state.tickConstraintsOrNull(tickIndex);
        if (tc == null || tc.getConstraints().isEmpty()) return java.util.Collections.emptyList();
        Vec3dCore foot = boxController.getPosition(tickIndex);
        if (foot == null) return java.util.Collections.emptyList();

        List<AABB> out = new ArrayList<>();
        for (Constraint c : tc.getConstraints()) {
            if (!c.isEnabled() || !ConstraintShapes.isDrawable(c)) continue;
            AABB box = ConstraintShapes.boxFor(c, foot);
            if (box != null) out.add(box);
        }
        return out;
    }

    /**
     * Content stamp over the drawable constraints and their anchor ticks. Folded into the cached
     * geometry's structural hash so editing a constraint (value, op, field, enable) rebuilds the
     * buffers even though the path positions are unchanged.
     */
    @Override
    public long revision() {
        if (!active.isActive()) return 0L;
        long h = 1L;
        for (Integer tickKey : state.populatedTicks()) {
            TickConstraints tc = state.tickConstraintsOrNull(tickKey);
            if (tc == null) continue;
            for (Constraint c : tc.getConstraints()) {
                if (!c.isEnabled() || !ConstraintShapes.isDrawable(c)) continue;
                h = 31 * h + tickKey;
                h = 31 * h + c.getField().ordinal();
                h = 31 * h + c.getOp().ordinal();
                h = 31 * h + Double.hashCode(c.getValue());
                h = 31 * h + Double.hashCode(c.getLo());
                h = 31 * h + Double.hashCode(c.getHi());
            }
        }
        return h;
    }
}
