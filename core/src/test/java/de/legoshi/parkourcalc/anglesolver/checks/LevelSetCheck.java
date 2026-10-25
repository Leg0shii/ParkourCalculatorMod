package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.Expect;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LevelSetAscent;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** {@link LevelSetAscent} on a keep-out-wall capture where the Solve For named by the sidecar ({@code axis},
 *  {@code goal}) degenerates the dual recovery (optimizing into a same-axis position wall). Seeded from the SLP
 *  hug the engine's reseeded path already produces, the level-set ladder must strictly beat it by at least
 *  {@code minGain} (a conservative floor well under the observed gain) and stay under the dual bound. */
final class LevelSetCheck implements ProblemCheck {

    private static final double EPS = 1.0e-6;

    @Override
    public void run(ProblemFixture pf) {
        Expect e = pf.expect;
        AngleSolverState.Axis axis = e.axis();
        AngleSolverState.Goal goal = e.goal();
        assertNotNull(pf.name + ": levelset needs \"axis\" in its .expect.json", axis);
        assertNotNull(pf.name + ": levelset needs \"goal\" in its .expect.json", goal);
        assertNotNull(pf.name + ": levelset needs \"minGain\" in its .expect.json", e.minGain);
        String id = pf.name + " " + axis + "/" + goal;

        ExactJumpModel model = pf.model;
        AtomicBoolean cancel = new AtomicBoolean(false);
        JumpSpec spec = pf.specFor(axis, goal);
        JumpPhysicsInputs sc = spec.asScenario();
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        double bound = ClosedFormSolve.dualBound(spec);
        assertTrue(id + ": expected a finite dual bound", !Double.isNaN(bound));

        double[] seed = altSeed(model, pf, axis, goal, cancel);
        assertNotNull(id + ": no cross-direction seed", seed);
        double[] hug = SlpSolve.optimize(model, spec, 0.0, cancel, seed);
        assertNotNull(id + ": plain SLP hug returned null", hug);
        double hugObj = Paths.objective(model, sc, spec, hug);

        double[] laddered = LevelSetAscent.improve(model, spec, hug, 0.0, cancel);
        assertNotNull(id + ": ladder returned null on a position-wall spec", laddered);
        double ladObj = Paths.objective(model, sc, spec, laddered);

        double viol = Paths.violation(model, sc, spec, laddered);
        assertTrue(id + ": ladder result not feasible (viol=" + viol + ")", viol <= 0.0);

        double gain = max ? ladObj - hugObj : hugObj - ladObj;
        System.out.printf("LEVELSET %-28s hug=%.5f -> %.5f bound=%.5f gain=%.5f%n", id, hugObj, ladObj, bound, gain);
        assertTrue(id + ": ladder gained only " + gain + " over the hug (need >= " + e.minGain + ")", gain >= e.minGain);
        assertTrue(id + ": ladder passed the dual bound (" + ladObj + " vs " + bound + ")",
                max ? ladObj <= bound + EPS : ladObj >= bound - EPS);
    }

    private static double[] altSeed(ExactJumpModel model, ProblemFixture pf, AngleSolverState.Axis axis,
                                    AngleSolverState.Goal goal, AtomicBoolean cancel) {
        for (AngleSolverState.Axis a : AngleSolverState.Axis.values()) {
            for (AngleSolverState.Goal g : AngleSolverState.Goal.values()) {
                if (a == axis && g == goal) continue;
                double[] w = ClosedFormSolve.optimize(model, pf.specFor(a, g), 0.0, cancel);
                if (w != null) return w;
            }
        }
        return null;
    }
}
