package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FacingLattice;
import de.legoshi.parkourcalc.core.anglesolver.solver.FacingPrefold;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FacingCellCompileTest {

    private static final int TICKS = 6;

    @Test
    public void pinWidthAdmitsEveryJointCell() {
        double widest = 0.0;
        for (boolean[] flags : new boolean[][] {{false, false, false}, {false, false, true}, {true, false, false}, {true, true, false}}) {
            for (int i = -720; i <= 720; i++) {
                float gf = i * 0.25f + 0.0071f;
                float[] cell = FacingLattice.jointCellInterval(gf, flags[0], flags[1], flags[2]);
                assertTrue(cell[0] <= gf && gf <= cell[1]);
                long id = FacingLattice.jointCellId(gf, flags[0], flags[1], flags[2]);
                assertEquals(id, FacingLattice.jointCellId(cell[0], flags[0], flags[1], flags[2]));
                assertEquals(id, FacingLattice.jointCellId(cell[1], flags[0], flags[1], flags[2]));
                assertTrue(FacingLattice.jointCellId(Math.nextDown(cell[0]), flags[0], flags[1], flags[2]) != id);
                assertTrue(FacingLattice.jointCellId(Math.nextUp(cell[1]), flags[0], flags[1], flags[2]) != id);
                widest = Math.max(widest, (double) cell[1] - (double) cell[0]);
            }
        }
        assertTrue("PIN_WIDTH_MAX must admit the widest joint cell: " + widest, widest <= FacingPrefold.PIN_WIDTH_MAX);
        assertTrue("PIN_WIDTH_MAX must stay within two buckets of a cell", FacingPrefold.PIN_WIDTH_MAX < 2.0 * FacingLattice.maxJointCellWidthDeg());
    }

    @Test
    public void absoluteFacingEqCompilesToItsJointCell() {
        AngleSolverState state = new AngleSolverState();
        AngleSolverEngine engine = engine(state, "1.8.9");
        state.tickConstraints(2).getConstraints().add(Constraint.scalar(Constraint.Field.F, Constraint.Op.EQ, 33.3));
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(spec);
        List<JumpConstraint> walls = facingWalls(spec);
        assertEquals(2, walls.size());
        double lo = Double.NaN;
        double hi = Double.NaN;
        for (JumpConstraint w : walls) {
            assertEquals(2, w.t1);
            if (w.cmp == JumpConstraint.Cmp.GE) lo = w.rhs;
            else hi = w.rhs;
        }
        assertTrue(lo <= 33.3 && 33.3 <= hi);
        assertTrue(hi - lo <= FacingPrefold.PIN_WIDTH_MAX);
        long id = FacingLattice.jointCellId((float) 33.3, false, false, false);
        assertEquals(id, FacingLattice.jointCellId((float) lo, false, false, false));
        assertEquals(id, FacingLattice.jointCellId((float) hi, false, false, false));
        assertFalse("the corridor is the cell, not a tolerance band", hi - lo == 2.0e-4);
        assertFalse(Double.isNaN(ClosedFormSolve.dualBound(spec)));
    }

    @Test
    public void edgeFloatTargetKeepsItsPinAndTheFullCell() {
        float[] cell = FacingLattice.jointCellInterval(33.3f, false, false, false);
        double[] targets = {cell[0], Math.nextDown((double) cell[0]), cell[1], Math.nextUp((double) cell[1])};
        for (double target : targets) {
            AngleSolverState state = new AngleSolverState();
            AngleSolverEngine engine = engine(state, "1.8.9");
            state.tickConstraints(2).getConstraints().add(Constraint.scalar(Constraint.Field.F, Constraint.Op.EQ, target));
            JumpSpec spec = engine.debugBuildSpec();
            assertNotNull(spec);
            double lo = Double.NaN;
            double hi = Double.NaN;
            for (JumpConstraint w : facingWalls(spec)) {
                assertEquals(target, w.pin, 0.0);
                if (w.cmp == JumpConstraint.Cmp.GE) lo = w.rhs;
                else hi = w.rhs;
            }
            assertEquals("window is the full cell for target " + target, (double) cell[0], lo, 0.0);
            assertEquals("window is the full cell for target " + target, (double) cell[1], hi, 0.0);
            FacingPrefold pre = FacingPrefold.analyze(spec.constraints, new JumpLinearModel(spec.asScenario()));
            assertNotNull("edge target must still prefold", pre);
            assertEquals("the prefold pins the typed value, not the cell midpoint", target, pre.pinnedYawAt(2), 0.0);
            assertFalse(Double.isNaN(ClosedFormSolve.dualBound(spec)));
        }
    }

    @Test
    public void userFacingRangeStillBlocksTheDualBound() {
        AngleSolverState state = new AngleSolverState();
        AngleSolverEngine engine = engine(state, "1.8.9");
        state.tickConstraints(2).getConstraints().add(Constraint.range(Constraint.Field.F, 20.0, 40.0, true, true));
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(spec);
        assertTrue(Double.isNaN(ClosedFormSolve.dualBound(spec)));
    }

    private static AngleSolverEngine engine(AngleSolverState state, String mcVersion) {
        InputData inputs = new InputData();
        BoxController boxes = new BoxController();
        for (int t = 0; t < TICKS; t++) {
            InputRow row = new InputRow();
            row.setKeyActive(InputRow.Key.W, true);
            row.setKeyActive(InputRow.Key.SPRINT, true);
            inputs.getRows().add(row);
        }
        for (int t = 0; t <= TICKS; t++) {
            boxes.add(new TickState(new Vec3dCore(0.5, 64.0, 0.5), true, false, false, 12.5f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN));
        }
        state.setStartTick(0);
        state.setLandingTick(TICKS);
        state.setDefaultSlipperiness(de.legoshi.parkourcalc.core.anglesolver.Slipperiness.DEFAULT);
        return new AngleSolverEngine(state, boxes, inputs, t -> { }, ExactJumpModel.forMcVersion(mcVersion));
    }

    private static List<JumpConstraint> facingWalls(JumpSpec spec) {
        List<JumpConstraint> out = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) if (c.mode == JumpConstraint.Mode.F) out.add(c);
        return out;
    }
}
