package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DirectionNoticeGateTest {

    private static GraphContext context() {
        ProblemFixture pf = ProblemFixture.load("solve", "j024-bfly-goal-direction");
        JumpSpec spec = pf.specFor(AngleSolverState.Axis.X, AngleSolverState.Goal.MAX);
        return new GraphContext(spec, pf.model, null, null, 0.0, new AtomicBoolean(false), null, true, null, null);
    }

    private static final Objective REQUESTED = new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, 10);
    private static final Objective ACTUAL = new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, 10);

    @Test
    public void unimprovedFallbackAttachesNotice() {
        GraphContext ctx = context();
        ctx.noteDirectionFallback(ACTUAL, 5.0);
        SolveResult r = new SolveResult(true, 1, 1, 1, 11);
        AngleSolverEngine.attachDirectionNotice(r, ctx, REQUESTED, 5.0);
        assertEquals(AngleSolverEngine.directionFallbackNotice("max X", "min Z"), r.getNotice());
        assertTrue(r.getNotice().contains("max X"));
        assertTrue(r.getNotice().contains("min Z"));
    }

    @Test
    public void improvedObjectiveSuppressesNotice() {
        GraphContext ctx = context();
        ctx.noteDirectionFallback(ACTUAL, 5.0);
        SolveResult r = new SolveResult(true, 1, 1, 1, 11);
        AngleSolverEngine.attachDirectionNotice(r, ctx, REQUESTED, 5.001);
        assertNull(r.getNotice());
    }

    @Test
    public void minSenseImprovesDownward() {
        Objective requestedMin = new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MIN, 10);
        GraphContext ctx = context();
        ctx.noteDirectionFallback(ACTUAL, 5.0);
        SolveResult worse = new SolveResult(true, 1, 1, 1, 11);
        AngleSolverEngine.attachDirectionNotice(worse, ctx, requestedMin, 5.0);
        assertEquals(AngleSolverEngine.directionFallbackNotice("min X", "min Z"), worse.getNotice());
        SolveResult better = new SolveResult(true, 1, 1, 1, 11);
        AngleSolverEngine.attachDirectionNotice(better, ctx, requestedMin, 4.999);
        assertNull(better.getNotice());
    }

    @Test
    public void noFallbackMeansNoNotice() {
        GraphContext ctx = context();
        SolveResult r = new SolveResult(true, 1, 1, 1, 11);
        AngleSolverEngine.attachDirectionNotice(r, ctx, REQUESTED, 5.0);
        assertNull(r.getNotice());
    }
}
