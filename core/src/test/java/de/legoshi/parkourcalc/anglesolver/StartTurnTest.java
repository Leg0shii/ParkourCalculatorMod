package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class StartTurnTest {

    private static final int TICKS = 4;
    private static final float START_YAW = -47.25f;
    private static final double[] FACINGS = {-45.0, -44.5, -43.0, -40.0};

    private final BoxController boxes = new BoxController();
    private final AngleSolverState state = new AngleSolverState();
    private InputData inputs;
    private AngleSolverEngine engine;

    @Before
    public void setUp() {
        inputs = new InputData();
        for (int t = 0; t < TICKS; t++) {
            InputRow row = new InputRow();
            row.setKeyActive(InputRow.Key.W, true);
            inputs.getRows().add(row);
        }
        for (int t = 0; t <= TICKS; t++) boxes.add(placeholder());
        state.setStartTick(0);
        state.setLandingTick(TICKS);
        engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, ExactJumpModel.forMcVersion("1.8.9"));
    }

    @Test
    public void wholeTurnsNamesTheTurnAFacingSitsIn() {
        assertEquals(0.0, Angles.wholeTurns(-45.0), 0.0);
        assertEquals(0.0, Angles.wholeTurns(180.0), 0.0);
        assertEquals(0.0, Angles.wholeTurns(-179.5), 0.0);
        assertEquals(360.0, Angles.wholeTurns(315.0), 0.0);
        assertEquals(360.0, Angles.wholeTurns(540.0), 0.0);
        assertEquals(-360.0, Angles.wholeTurns(-405.0), 0.0);
        assertEquals(-360.0, Angles.wholeTurns(-180.0), 0.0);
        assertEquals(1080.0, Angles.wholeTurns(984.6418), 0.0);
        assertEquals(360.0, Angles.wholeTurns((float) (START_YAW + 360.0)), 0.0);
    }

    @Test
    public void aJumpFromTickOneMayStartATurnAway() {
        JumpPhysicsInputs sc = scenario();
        assertTrue(sc.startTurnFree);
        assertFalse(sc.freeStartYaw);
    }

    @Test
    public void aFreeStartFacingMayStartATurnAwayAsWell() {
        addFacing(0, Constraint.Field.DF, Constraint.Op.EQ, 0.0);
        JumpPhysicsInputs sc = scenario();
        assertTrue(sc.startTurnFree);
        assertTrue(sc.freeStartYaw);
    }

    @Test
    public void aJumpInsideTheRunKeepsItsTurn() {
        state.setStartTick(1);
        assertFalse(scenario().startTurnFree);
    }

    @Test
    public void aLockedRowKeepsTheTurn() {
        InputRow row = inputs.getRows().get(2);
        row.setYawLocked(true);
        row.setYaw(-43.0f);
        assertFalse(scenario().startTurnFree);
    }

    @Test
    public void anAbsoluteFacingConstraintKeepsTheTurn() {
        addFacing(2, Constraint.Field.F, Constraint.Op.LE, -40.0);
        assertFalse(scenario().startTurnFree);
    }

    @Test
    public void aOneWayTurnOnTickOneKeepsTheTurn() {
        addFacing(0, Constraint.Field.DF, Constraint.Op.LE, 0.0);
        assertFalse(scenario().startTurnFree);
    }

    @Test
    public void aTurnLinkBetweenTicksLeavesTheTurnFree() {
        addFacing(2, Constraint.Field.DF, Constraint.Op.GE, 0.0);
        assertTrue(scenario().startTurnFree);
    }

    @Test
    public void aCopyCarriesTheTurnAndASliceDropsIt() {
        JumpPhysicsInputs sc = scenario();
        assertTrue(sc.copy().startTurnFree);
        JumpPhysicsInputs slice = sc.slice(0, sc.startPos.x, sc.startPos.y, sc.startPos.z, 0.0, 0.0, 0.0, sc.startYaw);
        assertFalse(slice.startTurnFree);
    }

    @Test
    public void aPinnedStartCarriesItsTurnThroughEveryTick() {
        JumpPhysicsInputs base = scenario();
        double[] here = base.toGameFacings(FACINGS);
        assertEquals(START_YAW + (float) (FACINGS[0] - (double) START_YAW), here[0], 0.0);
        for (double turns : new double[] {360.0, -360.0}) {
            JumpPhysicsInputs moved = base.copy();
            moved.startYaw = base.startYawAtTurns(turns);
            assertEquals(turns, Angles.wholeTurns(moved.startYaw), 0.0);
            assertEquals(Angles.wrap(START_YAW), Angles.wrap(moved.startYaw), 1.0e-4);
            double[] there = moved.toGameFacings(FACINGS);
            for (int k = 0; k < TICKS; k++) {
                assertEquals("tick " + k, turns, Angles.wholeTurns(there[k]), 0.0);
                assertEquals("tick " + k, FACINGS[k], Angles.wrap(there[k]), 1.0e-4);
            }
            assertEquals(Float.floatToIntBits(START_YAW), Float.floatToIntBits(moved.startYawAtTurns(0.0)));
        }
    }

    @Test
    public void aFreeStartFacingTakesTheTurnOfTheStartYaw() {
        addFacing(0, Constraint.Field.DF, Constraint.Op.EQ, 0.0);
        JumpPhysicsInputs base = scenario();
        assertEquals((float) FACINGS[0], base.toGameFacings(FACINGS)[0], 0.0);
        JumpPhysicsInputs moved = base.copy();
        moved.startYaw = base.startYawAtTurns(360.0);
        double[] there = moved.toGameFacings(FACINGS);
        assertEquals((float) (FACINGS[0] + 360.0), there[0], 0.0);
        assertEquals((float) (FACINGS[0] + 360.0) + (float) (FACINGS[1] - FACINGS[0]), there[1], 0.0);
        JumpPhysicsInputs slice = moved.slice(0, moved.startPos.x, moved.startPos.y, moved.startPos.z, 0.0, 0.0, 0.0,
                moved.startYaw);
        assertEquals(there[0], slice.toGameFacings(FACINGS)[0], 0.0);
    }

    @Test
    public void rowsWrittenFromAnotherTurnReplayTheSameFacings() {
        JumpPhysicsInputs base = scenario();
        float start = base.startYawAtTurns(360.0);
        JumpPhysicsInputs moved = base.copy();
        moved.startYaw = start;
        double[] game = moved.toGameFacings(FACINGS);
        AngleSolverEngine.writeYawRows(inputs.getRows(), 0, FACINGS, start);
        float entity = start;
        for (int k = 0; k < TICKS; k++) {
            entity = entity + inputs.getRows().get(k).getYaw();
            assertEquals("tick " + k, game[k], entity, 0.0);
        }
    }

    private JumpPhysicsInputs scenario() {
        assertNotNull(engine.debugBuildSpec());
        return engine.debugBuildSpec().asScenario();
    }

    private void addFacing(int tick, Constraint.Field field, Constraint.Op op, double value) {
        state.tickConstraints(tick).getConstraints().add(Constraint.scalar(field, op, value));
    }

    private static TickState placeholder() {
        return new TickState(new Vec3dCore(0.5, 64.0, 0.5), false, false, false, START_YAW,
                Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN);
    }
}
