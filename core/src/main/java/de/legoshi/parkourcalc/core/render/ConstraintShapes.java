package de.legoshi.parkourcalc.core.render;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

/**
 * Maps an angle-solver landing constraint to an in-world AABB anchored at the tick it
 * applies to (gh-145). Pure geometry: callers feed a constraint plus that tick's simulated
 * position and get back the box to outline (LINES) and fill (FACES); the renderer wiring lives
 * in {@link de.legoshi.parkourcalc.core.ui.BoxController}.
 *
 * <p>Only the <b>spatial</b> fields have a place in the world: {@link Constraint.Field#X} and
 * {@link Constraint.Field#Z} are world positions, so a bound on one is a plane the player must be
 * on/past. The facing/velocity fields (F, dX, dZ) are not positions and have no spatial extent at
 * the tick, so {@link #spatialAxis(Constraint)} returns null for them and they are not drawn.
 *
 * <p>The box is a thin horizontal plate sitting on the tick's foot position:
 * <ul>
 *   <li>On the constrained axis it spans the constrained interval.</li>
 *   <li>On the other horizontal axis it spans a fixed {@link #CROSS_HALF_WIDTH} either side of the
 *       tick, so the plate is centred on (and visibly tied to) the tick.</li>
 *   <li>In Y it is a fixed-height slab ({@link #SLAB_HEIGHT}) so it reads as a surface, not a line.</li>
 * </ul>
 *
 * <p>A bounded range {@code X ∈ [lo, hi]} maps to a band of exactly that width. An open-ended
 * comparison ({@code >}/{@code >=}/{@code <}/{@code <=}) can't be drawn in full (it is half-infinite),
 * so it renders a limited sub-area: the band starts at the bound and extends {@link #OPEN_EXTENT}
 * blocks in the open direction, which reads as "open this way" without being infinite. Equality
 * ({@code =}) renders a thin slab of width {@code 2 *} {@link #EQ_HALF_WIDTH} centred on the value.
 */
public final class ConstraintShapes {

    /** Half-width (blocks) of the plate on the axis the constraint does NOT bound; keeps it tied to the tick. */
    public static final double CROSS_HALF_WIDTH = 0.35;

    /** How far (blocks) an open-ended ({@code >}/{@code <}) band extends past its bound before it is clamped. */
    public static final double OPEN_EXTENT = 1.0;

    /** Half-width (blocks) of the thin slab drawn for an exact {@code =} constraint. */
    public static final double EQ_HALF_WIDTH = 0.02;

    /** Vertical thickness (blocks) of the plate, anchored at the tick's foot Y. */
    public static final double SLAB_HEIGHT = 0.06;

    private ConstraintShapes() {
    }

    /** The world axis a constraint occupies, or null when the field is non-spatial (F/dX/dZ) and not drawable. */
    public static AngleSolverState.Axis spatialAxis(Constraint c) {
        switch (c.getField()) {
            case X:
                return AngleSolverState.Axis.X;
            case Z:
                return AngleSolverState.Axis.Z;
            default:
                return null;
        }
    }

    /** True when this constraint maps to a world box (i.e. it bounds X or Z). */
    public static boolean isDrawable(Constraint c) {
        return spatialAxis(c) != null;
    }

    /**
     * The AABB for {@code c} anchored at {@code tickFoot} (the tick's simulated center-bottom, MC
     * convention). Returns null if the constraint is non-spatial. The interval on the constrained
     * axis is clamped for open-ended ops; the other horizontal axis and Y are fixed plate extents.
     */
    public static AABB boxFor(Constraint c, Vec3dCore tickFoot) {
        AngleSolverState.Axis axis = spatialAxis(c);
        if (axis == null) return null;

        double[] span = axisSpan(c);
        double lo = span[0];
        double hi = span[1];

        double y0 = tickFoot.y;
        double y1 = tickFoot.y + SLAB_HEIGHT;

        if (axis == AngleSolverState.Axis.X) {
            double zMin = tickFoot.z - CROSS_HALF_WIDTH;
            double zMax = tickFoot.z + CROSS_HALF_WIDTH;
            return new AABB(new Vec3dCore(lo, y0, zMin), new Vec3dCore(hi, y1, zMax));
        }
        double xMin = tickFoot.x - CROSS_HALF_WIDTH;
        double xMax = tickFoot.x + CROSS_HALF_WIDTH;
        return new AABB(new Vec3dCore(xMin, y0, lo), new Vec3dCore(xMax, y1, hi));
    }

    /**
     * The [lo, hi] interval on the constrained axis, already clamped to a finite sub-area for
     * open-ended ops. Package-visible for tests; callers should prefer {@link #boxFor}.
     */
    static double[] axisSpan(Constraint c) {
        if (c.isRange()) {
            double lo = Math.min(c.getLo(), c.getHi());
            double hi = Math.max(c.getLo(), c.getHi());
            return new double[]{lo, hi};
        }
        double v = c.getValue();
        switch (c.getOp()) {
            case GT:
            case GE:
                // open toward +axis: [v, v + OPEN_EXTENT]
                return new double[]{v, v + OPEN_EXTENT};
            case LT:
            case LE:
                // open toward -axis: [v - OPEN_EXTENT, v]
                return new double[]{v - OPEN_EXTENT, v};
            case EQ:
            default:
                return new double[]{v - EQ_HALF_WIDTH, v + EQ_HALF_WIDTH};
        }
    }
}
