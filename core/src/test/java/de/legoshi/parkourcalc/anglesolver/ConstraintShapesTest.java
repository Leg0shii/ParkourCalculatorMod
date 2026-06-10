package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * gh-98: every constraint field takes either form -- positions/facings accept ranges, velocities
 * accept the scalar comparisons. Pins the UI-Constraint -> solver-JumpConstraint mapping (a scalar
 * dX/dZ used to be forced through the range pair, compiling garbage walls from unset lo/hi) and the
 * scalar<->range op transitions on the model.
 */
public class ConstraintShapesTest {

    private static final int TICKS = 4;
    private static final double MET_TOL = 1.0e-3; // mirrors AngleSolverEngine.MET_TOL (EQ corridor width)

    /** Compiles a {@code TICKS}-tick segment (start tick 0, so absolute tick == segment tick). */
    private static JumpSpec compile(AngleSolverState state) {
        InputData inputs = new InputData();
        BoxController boxes = new BoxController();
        for (int t = 0; t < TICKS; t++) {
            InputRow row = new InputRow();
            row.setKeyActive(InputRow.Key.W, true);
            inputs.getRows().add(row);
            boxes.add(new TickState(new Vec3dCore(0.5, 64.0, 0.5), false, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN));
        }
        state.setStartTick(0);
        state.setLandingTick(TICKS);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { },
                ExactJumpModel.forMcVersion("1.8.9"));
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull("segment should compile", spec);
        return spec;
    }

    private static List<JumpConstraint> named(JumpSpec spec, String prefix) {
        List<JumpConstraint> out = new ArrayList<JumpConstraint>();
        for (JumpConstraint jc : spec.constraints) {
            if (jc.name.startsWith(prefix)) out.add(jc);
        }
        return out;
    }

    @Test
    public void scalarVelocityMapsToSingleWall() {
        AngleSolverState state = new AngleSolverState();
        state.tickConstraints(2).getConstraints().add(Constraint.scalar(Constraint.Field.DX, Constraint.Op.GE, 0.25));
        List<JumpConstraint> got = named(compile(state), "dX@2");
        assertEquals(1, got.size());
        JumpConstraint jc = got.get(0);
        assertEquals(JumpConstraint.Mode.X, jc.mode);
        assertEquals(JumpConstraint.Op.MINUS, jc.op);
        assertEquals(JumpConstraint.Cmp.GE, jc.cmp);
        assertEquals(2, jc.t1);
        assertEquals(Integer.valueOf(1), jc.t2);
        assertEquals(0.25, jc.rhs, 0.0);
    }

    @Test
    public void strictVelocityComparisonFoldsToWall() {
        // < and <= both compile to the LE wall (the solver's walls are byte-exact, see engine cmp()).
        AngleSolverState state = new AngleSolverState();
        state.tickConstraints(1).getConstraints().add(Constraint.scalar(Constraint.Field.DZ, Constraint.Op.LT, -0.1));
        List<JumpConstraint> got = named(compile(state), "dZ@1");
        assertEquals(1, got.size());
        assertEquals(JumpConstraint.Mode.Z, got.get(0).mode);
        assertEquals(JumpConstraint.Op.MINUS, got.get(0).op);
        assertEquals(JumpConstraint.Cmp.LE, got.get(0).cmp);
        assertEquals(-0.1, got.get(0).rhs, 0.0);
    }

    @Test
    public void velocityEqualityMapsToCorridor() {
        AngleSolverState state = new AngleSolverState();
        state.tickConstraints(2).getConstraints().add(Constraint.scalar(Constraint.Field.DX, Constraint.Op.EQ, 0.3));
        List<JumpConstraint> got = named(compile(state), "dX@2");
        assertEquals(2, got.size());
        for (JumpConstraint jc : got) {
            assertEquals(JumpConstraint.Op.MINUS, jc.op);
            double expected = jc.cmp == JumpConstraint.Cmp.GE ? 0.3 - MET_TOL : 0.3 + MET_TOL;
            assertEquals(jc.name, expected, jc.rhs, 0.0);
        }
    }

    @Test
    public void velocityRangeStillMapsToPair() {
        AngleSolverState state = new AngleSolverState();
        state.tickConstraints(3).getConstraints().add(Constraint.range(Constraint.Field.DZ, 0.1, 0.5, true, true));
        List<JumpConstraint> got = named(compile(state), "dZ@3");
        assertEquals(2, got.size());
        for (JumpConstraint jc : got) {
            assertEquals(JumpConstraint.Op.MINUS, jc.op);
            assertEquals(jc.cmp == JumpConstraint.Cmp.GE ? 0.1 : 0.5, jc.rhs, 0.0);
        }
    }

    @Test
    public void velocityOnFirstTickIsSkipped() {
        // No t-1 exists on the segment's first tick; the constraint is unmappable (and the panel
        // reports it as such) rather than mis-compiled.
        AngleSolverState state = new AngleSolverState();
        state.tickConstraints(0).getConstraints().add(Constraint.scalar(Constraint.Field.DX, Constraint.Op.GE, 0.2));
        assertTrue(named(compile(state), "dX@0").isEmpty());
    }

    @Test
    public void positionAndFacingRangesMapToPairs() {
        AngleSolverState state = new AngleSolverState();
        state.tickConstraints(1).getConstraints().add(Constraint.range(Constraint.Field.X, 1.0, 2.0, true, false));
        state.tickConstraints(2).getConstraints().add(Constraint.range(Constraint.Field.F, -5.0, 5.0, false, false));
        JumpSpec spec = compile(state);
        List<JumpConstraint> xs = named(spec, "X@1");
        assertEquals(2, xs.size());
        for (JumpConstraint jc : xs) {
            assertEquals(JumpConstraint.Mode.X, jc.mode);
            assertEquals(JumpConstraint.Op.PLUS, jc.op);
            assertEquals(jc.cmp == JumpConstraint.Cmp.GE ? 1.0 : 2.0, jc.rhs, 0.0);
        }
        List<JumpConstraint> fs = named(spec, "F@2");
        assertEquals(2, fs.size());
        for (JumpConstraint jc : fs) {
            assertEquals(JumpConstraint.Mode.F, jc.mode);
            assertEquals(jc.cmp == JumpConstraint.Cmp.GE ? -5.0 : 5.0, jc.rhs, 0.0);
        }
    }

    // ---- model: the op carries the form -----------------------------------------

    @Test
    public void enteringRangeSeedsBoundsFromValue() {
        Constraint c = Constraint.scalar(Constraint.Field.DX, Constraint.Op.GE, 0.25);
        c.setOp(Constraint.Op.IN);
        assertTrue(c.isRange());
        assertEquals(0.25, c.getLo(), 0.0);
        assertEquals(0.25, c.getHi(), 0.0);
        assertTrue("range defaults inclusive", c.isLoInclusive() && c.isHiInclusive());
    }

    @Test
    public void leavingRangeKeepsLowerBound() {
        Constraint c = Constraint.range(Constraint.Field.X, 1.5, 2.5, true, true);
        c.setOp(Constraint.Op.LE);
        assertTrue(!c.isRange());
        assertEquals(1.5, c.getValue(), 0.0);
    }

    @Test
    public void fieldSwitchKeepsForm() {
        Constraint scalar = Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 1.0);
        scalar.setField(Constraint.Field.DX);
        assertTrue(!scalar.isRange());
        assertEquals(Constraint.Op.GT, scalar.getOp());
        assertEquals(1.0, scalar.getValue(), 0.0);

        Constraint range = Constraint.range(Constraint.Field.F, -1.0, 1.0, true, true);
        range.setField(Constraint.Field.Z);
        assertTrue(range.isRange());
        assertEquals(-1.0, range.getLo(), 0.0);
        assertEquals(1.0, range.getHi(), 0.0);
    }
}
