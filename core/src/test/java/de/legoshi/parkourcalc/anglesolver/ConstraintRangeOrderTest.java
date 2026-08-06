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

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConstraintRangeOrderTest {

    private static final double EPS = 1.0e-9;
    private static final int TICKS = 4;

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
        assertNotNull(spec);
        return spec;
    }

    @Test
    public void reversedFactoryBoundsReadNormalized() {
        Constraint c = Constraint.range(Constraint.Field.X, -250.0, -251.0, true, true);
        assertEquals(-251.0, c.getLo(), EPS);
        assertEquals(-250.0, c.getHi(), EPS);
    }

    @Test
    public void reversedSetterBoundsReadNormalized() {
        Constraint c = Constraint.scalar(Constraint.Field.Z, Constraint.Op.EQ, -250.0);
        c.setOp(Constraint.Op.IN);
        c.setHi(-251.0);
        assertEquals(-251.0, c.getLo(), EPS);
        assertEquals(-250.0, c.getHi(), EPS);
    }

    @Test
    public void leavingAReversedRangeKeepsTheLowerBound() {
        Constraint c = Constraint.range(Constraint.Field.X, -250.0, -251.0, true, true);
        c.setOp(Constraint.Op.GE);
        assertEquals(-251.0, c.getValue(), EPS);
    }

    @Test
    public void reversedRangeCompilesSatisfiableBounds() {
        AngleSolverState state = new AngleSolverState();
        state.tickConstraints(2).getConstraints().add(Constraint.range(Constraint.Field.X, -250.0, -251.0, true, true));

        JumpSpec spec = compile(state);
        Double ge = null;
        Double le = null;
        for (JumpConstraint jc : spec.constraints) {
            if (!jc.name.startsWith("X@2")) continue;
            if (jc.cmp == JumpConstraint.Cmp.GE) ge = jc.rhs;
            if (jc.cmp == JumpConstraint.Cmp.LE) le = jc.rhs;
        }
        assertNotNull(ge);
        assertNotNull(le);
        assertEquals(-251.0, ge, EPS);
        assertEquals(-250.0, le, EPS);
        assertTrue("reversed bounds must still compile to a satisfiable pair", ge <= le);
    }
}
