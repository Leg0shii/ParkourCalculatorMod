package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SolveFailureDiagnosticsTest {

    private static final String PROBLEM = "j004";

    private static final class Ctx {
        final AngleSolverState state;
        final AngleSolverEngine engine;

        Ctx(AngleSolverState state, AngleSolverEngine engine) {
            this.state = state;
            this.engine = engine;
        }
    }

    private static Ctx build(Consumer<AngleSolverState> mutate) {
        ProblemFixture pf = ProblemFixture.load("closedform", PROBLEM);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(pf.file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(pf.file, state);
        state.setEffort(AngleSolverState.Effort.FAST);
        state.clearResult();
        mutate.accept(state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(pf.file), inputs, t -> { }, pf.model);
        return new Ctx(state, engine);
    }

    private static SolveResult solve(Ctx ctx) {
        ctx.engine.solve();
        long deadline = System.currentTimeMillis() + 120_000L;
        while (ctx.engine.isSolving() && System.currentTimeMillis() < deadline) {
            ctx.engine.poll();
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        ctx.engine.poll();
        return ctx.state.getResult();
    }

    private static SolveResult.Outcome unmetOutcomeAt(SolveResult r, String field, int displayTick) {
        for (SolveResult.Outcome o : r.getOutcomes()) {
            if (!o.met && field.equals(o.field) && ("T" + displayTick).equals(o.tick)) return o;
        }
        return null;
    }

    @Test
    public void unreachableWallStillReportsEveryConstraintAndTheMissedTick() {
        Ctx base = build(state -> { });
        SolveResult baseline = solve(base);
        assertTrue("baseline j004 must solve", baseline.isSuccess());
        boolean max = base.state.getGoal() == AngleSolverState.Goal.MAX;
        Constraint.Field axis = base.state.getAxis() == AngleSolverState.Axis.X
                ? Constraint.Field.X : Constraint.Field.Z;
        double target = baseline.getObjectiveValue() + (max ? 0.5 : -0.5);

        int[] landing = new int[1];
        Ctx ctx = build(state -> {
            landing[0] = state.getLandingTick();
            state.tickConstraints(landing[0]).getConstraints().add(Constraint.scalar(
                    axis, max ? Constraint.Op.GE : Constraint.Op.LE, target));
        });
        SolveResult r = solve(ctx);

        assertNotNull("engine returned no result", r);
        assertFalse("a wall half a block past the optimum must not solve", r.isSuccess());
        assertTrue("a failed solve must still report the constraints it was judged against",
                r.getTotal() > 0);
        assertFalse("a failed solve must still list per-constraint outcomes", r.getOutcomes().isEmpty());
        assertTrue("a failed solve must report which constraints missed", r.getMet() < r.getTotal());

        SolveResult.Outcome missed = unmetOutcomeAt(r, axis.label, landing[0] + 1);
        assertNotNull("the unreachable wall is not reported as an unmet outcome row", missed);
        assertFalse("an unmet row must carry a margin the panel can colour", missed.margin.isEmpty());
        assertTrue("the missed tick must be flagged for the world overlay",
                r.getUnmetTicks().contains(landing[0]));
        assertEquals("the search reached a near-miss, so it must be reported instead of the current path",
                "closest attempt", detail(r, "Values from"));
        assertNotNull("a near-miss must report how far off it was", detail(r, "Worst violation"));
    }

    @Test
    public void declinedSolveFallsBackToTheCurrentPath() {
        int[] at = new int[1];
        Ctx ctx = build(state -> {
            at[0] = state.getStartTick() + 2;
            state.tickConstraints(at[0]).getConstraints()
                    .add(Constraint.scalar(Constraint.Field.DF, Constraint.Op.LE, 0.0));
        });
        SolveResult r = solve(ctx);

        assertNotNull("engine returned no result", r);
        assertFalse("a retired dF inequality must not solve", r.isSuccess());
        assertEquals("failed solve must name the unsupported dF as the cause",
                AngleSolverEngine.DF_UNSUPPORTED_NOTICE, r.getNotice());
        assertFalse("a declined solve must still report the current path's values", r.getOutcomes().isEmpty());
        assertEquals("current path", detail(r, "Values from"));
    }

    @Test
    public void successfulSolveIsUnaffected() {
        SolveResult r = solve(build(state -> { }));
        assertNotNull("engine returned no result", r);
        assertTrue("baseline j004 must still solve", r.isSuccess());
        assertEquals(r.getTotal(), r.getMet());
    }

    private static String detail(SolveResult r, String label) {
        for (SolveResult.Detail d : r.getDetails()) {
            if (label.equals(d.label)) return d.value;
        }
        return null;
    }
}
