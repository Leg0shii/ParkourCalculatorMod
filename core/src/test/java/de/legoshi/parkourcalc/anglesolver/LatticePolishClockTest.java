package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LatticePolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveClock;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LatticePolishClockTest {

    private static final String CAPTURE = "j013-cw2cwwinged";
    private static final long WORK_UNITS_PER_MILLION = 1_000_000L;
    private static final long FULL_SEARCH_NANOS = 100_000_000L;
    private static final long BUDGET_NANOS = 40_000_000L;
    private static final long OVERSHOOT_NANOS = 20_000_000L;

    private static final class Run {
        final String result;
        final long workNanos;
        final long polishUnits;
        final String work;

        Run(String result, long workNanos, long polishUnits, String work) {
            this.result = result;
            this.workNanos = workNanos;
            this.polishUnits = polishUnits;
            this.work = work;
        }
    }

    @Test
    public void thePolishChargesItsOwnWork() {
        Run full = polish(0L);
        assertTrue("the Newton rounds and the grid search charge the clock: " + full.work, full.polishUnits > 0L);
        assertTrue("an Optimize search of this jump is more than " + FULL_SEARCH_NANOS + " ns of work, counted "
                + full.workNanos, full.workNanos > FULL_SEARCH_NANOS);
    }

    @Test
    public void aWorkBudgetEndsThePolishAtTheSamePoint() {
        Run full = polish(0L);
        Run first = polish(BUDGET_NANOS);
        Run second = polish(BUDGET_NANOS);
        assertTrue("the budget ends the search early: " + first.workNanos + " of " + full.workNanos,
                first.workNanos < full.workNanos);
        assertTrue("the search ran " + first.workNanos + " ns of work on a budget of " + BUDGET_NANOS,
                first.workNanos < BUDGET_NANOS + OVERSHOOT_NANOS);
        assertEquals("the same budget ends on the same result", first.result, second.result);
        assertEquals("the same budget counts the same work", first.work, second.work);
        assertEquals(first.workNanos, second.workNanos);
    }

    private static Run polish(long budgetNanos) {
        ProblemFixture pf = ProblemFixture.load("solve", CAPTURE);
        JumpSpec spec = pf.rig().spec();
        JumpPhysicsInputs sc = spec.asScenario();
        double[] seed = ClosedFormSolve.optimize(pf.model, spec, 0.0, new AtomicBoolean(false));
        assertNotNull(CAPTURE + ": closed-form seed", seed);
        LatticePolish.Config cfg = new LatticePolish.Config();
        cfg.heavyCombos = BuiltinGraphs.POLISH_FACINGS;
        cfg.closures = BuiltinGraphs.POLISH_SEARCHES;
        cfg.halfCap = BuiltinGraphs.POLISH_TABLE;
        cfg.window = BuiltinGraphs.POLISH_WINDOW;
        cfg.workUnits = BuiltinGraphs.POLISH_WORK * WORK_UNITS_PER_MILLION;
        AtomicBoolean token = new AtomicBoolean(false);
        SolveClock clock = SolveClock.install(null, 0L, 0L);
        try {
            long start = clock.elapsedNanos();
            long deadline = budgetNanos > 0L ? SolveClock.nanoTime() + budgetNanos : 0L;
            if (deadline > 0L) clock.arm(token, deadline);
            LatticePolish.Result r = LatticePolish.polish(pf.model, spec, Angles.wrapAll(seed), sc.startPos.x,
                    sc.startPos.z, cfg, deadline, token);
            String result = r == null ? "kept"
                    : Arrays.toString(r.yaws) + " " + r.startX + " " + r.startZ + " " + r.startYaw + " " + r.objective;
            return new Run(result, clock.elapsedNanos() - start, clock.units(SolveClock.POLISH), clock.describe());
        } finally {
            clock.uninstall();
        }
    }
}
