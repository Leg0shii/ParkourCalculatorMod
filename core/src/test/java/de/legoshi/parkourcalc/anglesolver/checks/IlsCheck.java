package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.IlsPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The ILS polish on the capture: from the closed form's byte-exact feasible seed it stays feasible and never
 *  regresses the objective, and an infeasible seed (all-zero yaws, when those are infeasible) comes back
 *  untouched. {@code seedMayMiss} lets a capture whose closed form does not seed skip the first half. */
final class IlsCheck implements ProblemCheck {

    @Override
    public void run(ProblemFixture pf) {
        String name = pf.name;
        ExactJumpModel model = pf.model;
        JumpSpec spec = pf.specFor(null, null);
        JumpPhysicsInputs sc = spec.asScenario();
        Objective obj = spec.objective;
        boolean max = obj.sense == Objective.Sense.MAX;
        AtomicBoolean cancel = new AtomicBoolean(false);

        double[] seed = ClosedFormSolve.optimize(model, spec, 0.0, cancel);
        boolean seedMayMiss = pf.expect.flag(pf.expect.seedMayMiss, false);
        if (seed != null || !seedMayMiss) {
            assertNotNull(name + ": closed-form seed", seed);
            seed = Angles.wrapAll(seed);
            assertTrue(name + ": seed must be byte-exact feasible", Paths.feasible(model, sc, spec, seed));
            double seedObj = Paths.objective(model, sc, spec, seed);

            long deadline = System.nanoTime() + 2_000_000_000L;
            double[] out = IlsPolish.polish(model, spec, seed, deadline, 1, false, cancel, null);
            assertNotNull(name + ": ILS returned null", out);
            assertTrue(name + ": ILS result must stay byte-exact feasible", Paths.feasible(model, sc, spec, out));
            double outObj = Paths.objective(model, sc, spec, out);
            double gain = max ? outObj - seedObj : seedObj - outObj;
            System.out.printf("ILS %-28s seed=%.6f -> %.6f gain=%.3e%n", name, seedObj, outObj, gain);
            assertTrue(name + ": ILS regressed the objective by " + (-gain), gain >= -1e-9);
        }

        double[] junk = new double[sc.numTicks];
        Arrays.fill(junk, 0.0);
        if (Paths.feasible(model, sc, spec, junk)) return;
        double[] out = IlsPolish.polish(model, spec, junk, System.nanoTime() + 1_000_000_000L, 2, false, cancel, null);
        assertNotNull(name + ": ILS returned null on an infeasible seed", out);
        assertTrue(name + ": an infeasible seed must come back untouched", Arrays.equals(Angles.wrapAll(junk), out));
    }
}
