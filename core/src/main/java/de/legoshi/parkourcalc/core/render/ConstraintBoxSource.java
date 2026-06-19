package de.legoshi.parkourcalc.core.render;

import java.util.List;

/**
 * Per-tick supply of constraint plates for the world overlay (gh-145). Decouples
 * {@link de.legoshi.parkourcalc.core.ui.BoxController} from the angle-solver model: the
 * implementation owns the {@link de.legoshi.parkourcalc.core.anglesolver.AngleSolverState} and
 * turns each tick's drawable constraints into world {@link ConstraintPlate}s via {@link ConstraintShapes}.
 *
 * <p>{@code platesAt(tickIndex)} is called with a {@link de.legoshi.parkourcalc.core.ui.BoxController}
 * tick index (same index space as the simulated path), so the constraint->tick mapping stays explicit:
 * the plates returned are anchored at that tick's simulated position and carry their own type
 * (include/exclude) and satisfied status.
 */
public interface ConstraintBoxSource {

    /** Empty source: no constraint geometry. */
    ConstraintBoxSource NONE = new ConstraintBoxSource() {
        @Override
        public List<ConstraintPlate> platesAt(int tickIndex) {
            return java.util.Collections.emptyList();
        }

        @Override
        public long revision() {
            return 0L;
        }
    };

    /** Drawable constraint plates anchored at the given tick, or an empty list if none. Never null. */
    List<ConstraintPlate> platesAt(int tickIndex);

    /**
     * Monotonic-ish content stamp: changes whenever the constraint geometry would change (edited
     * constraints, axis/value edits, etc.). Folded into the cached-geometry structural hash so the
     * loaders rebuild their buffers when constraints change but the path positions do not.
     */
    long revision();
}
