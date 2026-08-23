package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.IlsPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import org.junit.experimental.categories.Category;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class IlsPolishTest {

    @Test
    public void staysFeasibleAndNeverRegresses() {
        ProblemFixture pf = ProblemFixture.load("solve", "j016-X2jmmp2p");
        ExactJumpModel model = pf.model;
        JumpSpec spec = pf.specFor(null, null);
        JumpPhysicsInputs sc = spec.asScenario();
        Objective obj = spec.objective;
        boolean max = obj.sense == Objective.Sense.MAX;
        AtomicBoolean cancel = new AtomicBoolean(false);

        double[] seed = ClosedFormSolve.optimize(model, spec, 0.0, cancel);
        assertNotNull("closed-form seed", seed);
        seed = Angles.wrapAll(seed);
        assertTrue("seed must be byte-exact feasible", feasible(spec, sc, model, seed));
        double seedObj = objectiveOf(model, sc, obj, seed);

        long deadline = System.nanoTime() + 2_000_000_000L;
        double[] out = IlsPolish.polish(model, spec, seed, deadline, 1, false, cancel, null);
        assertNotNull(out);
        assertTrue("ILS result must stay byte-exact feasible", feasible(spec, sc, model, out));

        double outObj = objectiveOf(model, sc, obj, out);
        double gain = max ? outObj - seedObj : seedObj - outObj;
        assertTrue("ILS regressed the objective by " + (-gain), gain >= -1e-9);
    }

    @Test
    public void infeasibleSeedReturnedUnchanged() {
        ProblemFixture pf = ProblemFixture.load("solve", "j021-rinav1-01");
        ExactJumpModel model = pf.model;
        JumpSpec spec = pf.specFor(null, null);
        JumpPhysicsInputs sc = spec.asScenario();
        AtomicBoolean cancel = new AtomicBoolean(false);

        double[] junk = new double[sc.numTicks];
        java.util.Arrays.fill(junk, 0.0);
        if (feasible(spec, sc, model, junk)) return; // all-zero happens to be feasible: nothing to assert
        double[] out = IlsPolish.polish(model, spec, junk, System.nanoTime() + 1_000_000_000L, 2, false, cancel, null);
        assertNotNull(out);
        assertTrue("an infeasible seed must come back untouched", java.util.Arrays.equals(Angles.wrapAll(junk), out));
    }

    private boolean feasible(JumpSpec spec, JumpPhysicsInputs sc, ExactJumpModel model, double[] yaws) {
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = model.forward(sc, gf);
        return c.maxViolation(gf, path) <= 0.0;
    }

    private static double objectiveOf(ExactJumpModel model, JumpPhysicsInputs sc, Objective obj, double[] absWrapped) {
        return model.forward(sc, sc.toGameFacings(absWrapped)).getPos(obj.tick, obj.axis);
    }
}
