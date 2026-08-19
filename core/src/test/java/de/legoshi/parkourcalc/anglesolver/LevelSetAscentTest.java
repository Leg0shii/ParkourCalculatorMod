package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LevelSetAscent;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** {@link LevelSetAscent} on keep-out-wall captures where the chosen Solve For degenerates the dual recovery
 *  (optimizing into a same-axis position wall). Seeded from the SLP hug the engine's reseeded path already
 *  produces, the level-set ladder must strictly beat it, reaching close to the dual bound. Without the
 *  ladder these directions land pointing the wrong way (e.g. j003 X/MIN reaches only -27 of a -31 optimum). */
@Category(SlowSolverTests.class)
public class LevelSetAscentTest {

    private static final double EPS = 1.0e-6;

    /** capture, axis, goal, and a conservative floor on how much the ladder must beat the plain SLP hug by
     *  (well under the observed gain, so model-precision drift does not make it flaky). */
    private static final Object[][] CASES = {
        {"closedform", "j012-pistonbasesidewallbf", AngleSolverState.Axis.Z, AngleSolverState.Goal.MAX, 0.5},
        {"solve",      "j003",                      AngleSolverState.Axis.X, AngleSolverState.Goal.MIN, 2.0},
        {"solve",      "j008-bfneo",                AngleSolverState.Axis.Z, AngleSolverState.Goal.MIN, 0.3},
        {"solve",      "taser-80t",                 AngleSolverState.Axis.X, AngleSolverState.Goal.MIN, 0.2},
    };

    @Test
    public void ladderStrictlyBeatsThePlainHugTowardTheBound() {
        for (Object[] c : CASES) {
            String cat = (String) c[0], name = (String) c[1];
            AngleSolverState.Axis axis = (AngleSolverState.Axis) c[2];
            AngleSolverState.Goal goal = (AngleSolverState.Goal) c[3];
            double minGain = (Double) c[4];
            String id = name + " " + axis + "/" + goal;

            ProblemFixture pf = ProblemFixture.load(cat, name);
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
            double hugObj = objective(model, sc, spec, hug);

            double[] laddered = LevelSetAscent.improve(model, spec, hug, 0.0, cancel);
            assertNotNull(id + ": ladder returned null on a position-wall spec", laddered);
            double ladObj = objective(model, sc, spec, laddered);

            double viol = violation(model, sc, spec, laddered);
            assertTrue(id + ": ladder result not feasible (viol=" + viol + ")", viol <= 0.0);

            double gain = max ? ladObj - hugObj : hugObj - ladObj;
            System.out.printf("LEVELSET %-28s hug=%.5f -> %.5f bound=%.5f gain=%.5f%n",
                    id, hugObj, ladObj, bound, gain);
            assertTrue(id + ": ladder gained only " + gain + " over the hug (need >= " + minGain + ")",
                    gain >= minGain);
            assertTrue(id + ": ladder passed the dual bound (" + ladObj + " vs " + bound + ")",
                    max ? ladObj <= bound + EPS : ladObj >= bound - EPS);
        }
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

    private static double objective(ExactJumpModel model, JumpPhysicsInputs sc, JumpSpec spec, double[] yawsAbs) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        return model.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
    }

    private static double violation(ExactJumpModel model, JumpPhysicsInputs sc, JumpSpec spec, double[] yawsAbs) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        ForwardPath path = model.forward(sc, gf);
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, path);
    }
}
