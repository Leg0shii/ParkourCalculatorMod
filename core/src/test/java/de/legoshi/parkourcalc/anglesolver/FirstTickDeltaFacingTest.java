package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FirstTickDeltaFacingTest {

    private static final int TICKS = 3;
    private static final float START_YAW = 12.5f;
    private static final double MET_TOL = 1.0e-4;

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
        for (int t = 0; t <= TICKS; t++) boxes.add(placeholder(0.5, 64.0, 0.5));
        state.setStartTick(0);
        state.setLandingTick(TICKS);
        engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, ExactJumpModel.forMcVersion("1.8.9"));
    }

    @Test
    public void zeroTurnOnTickOneFreesTheStartFacing() {
        addDf(0, Constraint.Op.EQ);
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(spec);
        assertTrue("no seam wall may remain: " + facingWalls(spec), facingWalls(spec).isEmpty());
        assertTrue("tick 0 must realize its facing as an absolute", spec.asScenario().yawLockedPerTick[0]);
    }

    @Test
    public void seamInsideALaterWindowStillPinsToThePreviousFacing() {
        state.setStartTick(1);
        addDf(1, Constraint.Op.EQ);
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(spec);
        List<JumpConstraint> walls = facingWalls(spec);
        assertEquals(2, walls.size());
        float seed = spec.asScenario().startYaw;
        for (JumpConstraint w : walls) {
            assertEquals(0, w.t1);
            assertNull(w.t2);
            assertEquals(JumpConstraint.Op.PLUS, w.op);
            assertEquals(seed, w.rhs, MET_TOL + 1.0e-9);
        }
        assertFalse(spec.asScenario().yawLockedPerTick[0]);
    }

    @Test
    public void unsupportedShapeOnTickOneKeepsTheSeamWall() {
        addDf(0, Constraint.Op.LE);
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(spec);
        assertEquals(1, facingWalls(spec).size());
        assertFalse(spec.asScenario().yawLockedPerTick[0]);
    }

    @Test
    public void applyMovesTheStartFacingAndZeroesTickOne() {
        addDf(0, Constraint.Op.EQ);
        SolveResult r = solve();
        assertTrue("the free-facing solve must succeed", r.isSuccess());
        SolveResult.Outcome df = null;
        for (SolveResult.Outcome o : r.getOutcomes()) if ("dF".equals(o.field)) df = o;
        assertNotNull("dF outcome row missing", df);
        assertTrue("the Tick 1 dF must report met against the solved start facing", df.met);

        double[] yaws = new double[r.getYaws().size()];
        for (int k = 0; k < yaws.length; k++) yaws[k] = r.getYaws().get(k).yaw;
        float solvedStart = (float) yaws[0];
        assertTrue("fixture must move the start facing", solvedStart != START_YAW);

        AtomicReference<Float> moved = new AtomicReference<>();
        engine.setOnStartYawChanged(moved::set);
        engine.apply();
        assertNotNull("Apply must publish the new start facing", moved.get());
        assertEquals(solvedStart, moved.get(), 0f);

        List<InputRow> rows = inputs.getRows();
        InputRow first = rows.get(0);
        assertEquals(first.isYawLocked() ? solvedStart : 0f, first.getYaw(), 0f);

        JumpPhysicsInputs sc = engine.lastSpecDebug().asScenario().copy();
        boolean[] locks = new boolean[TICKS];
        for (int k = 0; k < TICKS; k++) locks[k] = k == 0 || rows.get(k).isYawLocked();
        sc.yawLockedPerTick = locks;
        double[] game = sc.toGameFacings(yaws);
        float entity = moved.get();
        for (int k = 0; k < TICKS; k++) {
            InputRow row = rows.get(k);
            entity = row.isYawLocked() ? row.getYaw() : entity + row.getYaw();
            assertEquals("row replay must reproduce the solved facing at tick " + k, game[k], entity, 0.0);
        }
    }

    private void addDf(int tick, Constraint.Op op) {
        state.tickConstraints(tick).getConstraints().add(Constraint.scalar(Constraint.Field.DF, op, 0.0));
    }

    private static List<JumpConstraint> facingWalls(JumpSpec spec) {
        List<JumpConstraint> out = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) if (c.mode == JumpConstraint.Mode.F) out.add(c);
        return out;
    }

    private SolveResult solve() {
        engine.solve();
        long deadline = System.currentTimeMillis() + 30_000L;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        engine.poll();
        SolveResult r = state.getResult();
        assertNotNull("engine returned no result", r);
        return r;
    }

    private static TickState placeholder(double x, double y, double z) {
        return new TickState(new Vec3dCore(x, y, z), false, false, false, START_YAW,
                Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN);
    }
}
