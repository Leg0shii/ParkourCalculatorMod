package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LatticePolishDeadlineTest {

    private static final int TICKS = 6;
    private static final float START_YAW = -47.25f;
    private static final double[] FACINGS = {-45.0, -44.5, -43.0, -40.0, -38.0, -37.0};
    private static final double START = 0.5;
    private static final int OUTER_ROUNDS = 40;
    private static final long UNITS = 100_000_000L;

    private ExactJumpModel exact;
    private JumpSpec spec;

    private static final class CountingStop implements BooleanSupplier {
        final int fireAt;
        int calls;

        CountingStop(int fireAt) {
            this.fireAt = fireAt;
        }

        @Override
        public boolean getAsBoolean() {
            calls++;
            return calls >= fireAt;
        }
    }

    @Before
    public void setUp() {
        InputData inputs = new InputData();
        for (int t = 0; t < TICKS; t++) {
            InputRow row = new InputRow();
            row.setKeyActive(InputRow.Key.W, true);
            inputs.getRows().add(row);
        }
        BoxController boxes = new BoxController();
        for (int t = 0; t <= TICKS; t++) {
            boxes.add(new TickState(new Vec3dCore(START, 64.0, START), false, false, false, START_YAW,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN));
        }
        AngleSolverState state = new AngleSolverState();
        state.setStartTick(0);
        state.setLandingTick(TICKS);
        exact = ExactJumpModel.forMcVersion("1.8.9");
        spec = new AngleSolverEngine(state, boxes, inputs, t -> { }, exact).debugBuildSpec();
        assertNotNull(spec);
    }

    @Test
    public void workStopsOnTheRoundTheStopFires() {
        CountingStop stop = new CountingStop(3);
        TrigRowModel.Work work = new TrigRowModel.Work(UNITS, stop);
        assertTrue(work.spend(1));
        assertTrue(work.spend(1));
        assertFalse(work.spend(1));
        assertTrue(work.left < 0);
        assertFalse(work.spend(1));
        assertEquals(3, stop.calls);
    }

    @Test
    public void workWithoutAStopOnlyCountsUnits() {
        TrigRowModel.Work work = new TrigRowModel.Work(2);
        assertTrue(work.spend(1));
        assertTrue(work.spend(1));
        assertFalse(work.spend(1));
    }

    @Test
    public void theSmoothSolveAsksTheStopEveryRound() {
        CountingStop never = new CountingStop(Integer.MAX_VALUE);
        assertTrue(smoothSolve(new TrigRowModel.Work(UNITS, never)));
        assertTrue("rounds " + never.calls, never.calls >= 3);

        CountingStop second = new CountingStop(2);
        TrigRowModel.Work work = new TrigRowModel.Work(UNITS, second);
        assertFalse(smoothSolve(work));
        assertEquals(2, second.calls);
        assertTrue(work.left < 0);
    }

    @Test
    public void aStopThatNeverFiresLeavesTheSolveAsItWas() {
        TrigRowModel.Work plain = new TrigRowModel.Work(UNITS);
        TrigRowModel.Work watched = new TrigRowModel.Work(UNITS, new CountingStop(Integer.MAX_VALUE));
        assertTrue(smoothSolve(plain));
        assertTrue(smoothSolve(watched));
        assertEquals(plain.left, watched.left);
    }

    @Test
    public void aCancelledStageReturnsNothing() {
        LatticePolish.Config cfg = new LatticePolish.Config();
        LatticePolish.Result open = LatticePolish.polish(exact, spec, FACINGS, START, START, cfg, 0L, null);
        assertNotNull(open);
        LatticePolish.Result watched = LatticePolish.polish(exact, spec, FACINGS, START, START, cfg, 0L,
                new AtomicBoolean(false));
        assertNotNull(watched);
        assertArrayEquals(open.yaws, watched.yaws, 0.0);
        assertNull(LatticePolish.polish(exact, spec, FACINGS, START, START, cfg, 0L, new AtomicBoolean(true)));
        LatticePolish.Config wide = new LatticePolish.Config();
        wide.window = 64;
        wide.halfCap = 30000;
        assertNull(LatticePolish.polish(exact, spec, FACINGS, START, START, wide, 0L, new AtomicBoolean(true)));
    }

    private boolean smoothSolve(TrigRowModel.Work work) {
        double[] yaws = Angles.wrapAll(FACINGS.clone());
        TrigRowModel model = new TrigRowModel(exact, spec);
        model.build(yaws, START, START);
        model.calibrate(yaws, START, START);
        double[] z = model.pointOf(model.effective(yaws, START, START));
        double[] mu = new double[model.rowCount];
        return model.solve(z, mu, OUTER_ROUNDS, false, work);
    }
}
