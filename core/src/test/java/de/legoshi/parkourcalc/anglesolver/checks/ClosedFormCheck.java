package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The closed-form solve is byte-exact feasible, on objective, and fast. */
final class ClosedFormCheck implements ProblemCheck {

    @Override
    public void run(ProblemFixture pf) {
        String name = pf.name;
        double refObj = referenceObjective(pf);
        ExactJumpModel exact = pf.model;
        JumpSpec spec = pf.specFor(null, null);
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);

        AtomicBoolean cancel = new AtomicBoolean(false);
        double[] yaws = ClosedFormSolve.optimize(exact, spec, 0.0, cancel);
        assertNotNull(name + ": closed form returned null (no feasible solve)", yaws);

        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = exact.forward(sc, gf);
        double viol = compiled.maxViolation(gf, path);
        assertTrue(name + ": not byte-exact feasible (viol=" + viol + ")", viol <= 0.0);

        double obj = path.getPos(spec.objective.tick, spec.objective.axis);
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        double objGap = max ? refObj - obj : obj - refObj;
        assertTrue(name + ": objective regressed vs reference by " + objGap, objGap <= pf.expect.maxObjectiveGap());

        for (int i = 0; i < 200; i++) ClosedFormSolve.optimize(exact, spec, 0.0, cancel);
        int reps = 200;
        long t0 = System.nanoTime();
        for (int i = 0; i < reps; i++) ClosedFormSolve.optimize(exact, spec, 0.0, cancel);
        double usEach = (System.nanoTime() - t0) / 1e3 / reps;

        System.out.printf("CLOSED %-12s n=%d  %.1f us  viol=%.2e  obj=%.6f ref=%.6f gap=%.2e%n",
                name, sc.numTicks, usEach, viol, obj, refObj, objGap);
        assertTrue(name + ": solve too slow (" + usEach + " us > " + pf.expect.maxMicros() + ")",
                usEach < pf.expect.maxMicros());
    }

    /** The recorded in-game objective the closed form must not regress past: from the sidecar's
     *  {@code refObjective}, or the capture's own {@code result.objectiveValue} if it still carries one. */
    private static double referenceObjective(ProblemFixture pf) {
        if (pf.expect.refObjective != null) return pf.expect.refObjective;
        if (pf.file.angleSolver.result != null) return pf.file.angleSolver.result.objectiveValue;
        throw new IllegalStateException(pf.name + ": closedform needs \"refObjective\" in its .expect.json");
    }
}
