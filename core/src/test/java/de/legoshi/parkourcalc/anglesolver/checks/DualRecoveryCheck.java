package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

/** The deterministic dual chain (closed form, SLP, reseeded SLP, relaxation recovery) byte-exact-solves the
 *  capture in its saved direction, no CMA-ES, no warm start; a sidecar {@code bnbSeconds} adds a bounded blind
 *  pattern-B&B fallback on a chain miss or target shortfall. */
final class DualRecoveryCheck implements ProblemCheck {

    @Override
    public void run(ProblemFixture pf) {
        String name = pf.name;
        ExactJumpModel exact = pf.model;
        JumpSpec spec = pf.specFor(null, null);
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);

        AtomicBoolean cancel = new AtomicBoolean(false);
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        Double target = pf.expect.refObjective;
        long t0 = System.nanoTime();
        String[] chain = new String[1];
        double[] yaws = AngleSolverEngine.dualChain(exact, spec, sc, cancel, chain);
        boolean shortOfTarget = false;
        if (yaws != null && target != null) {
            double o = Paths.objective(exact, sc, spec, yaws);
            shortOfTarget = (max ? target - o : o - target) > pf.expect.maxObjectiveGap();
        }
        if ((yaws == null || shortOfTarget) && pf.expect.bnbSeconds != null) {
            double stopAt = target != null ? target : (max ? -1.0e300 : 1.0e300);
            double[] bnb = BoundPrunedRecovery.solve(exact, spec, 0.0, cancel, pf.expect.bnbSeconds * 1_000_000_000L, stopAt);
            if (bnb != null && (yaws == null || (max ? Paths.objective(exact, sc, spec, bnb) > Paths.objective(exact, sc, spec, yaws)
                                                     : Paths.objective(exact, sc, spec, bnb) < Paths.objective(exact, sc, spec, yaws)))) {
                yaws = bnb;
                chain[0] = target != null ? "pattern B&B (to target)" : "pattern B&B (first feasible)";
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        boolean solved = false;
        double obj = Double.NaN;
        if (yaws != null) {
            double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
            ForwardPath path = exact.forward(sc, gf);
            double viol = compiled.maxViolation(gf, path);
            assertTrue(name + ": chain result not byte-exact feasible (viol=" + viol + ")", viol <= 0.0);
            solved = true;
            obj = path.getPos(spec.objective.tick, spec.objective.axis);
        }
        System.out.printf("DUAL %-44s solved=%-5s via=%-32s %4d ms%s%n",
                name, solved, solved ? chain[0] : "-", ms, solved ? String.format("  obj=%.6f", obj) : "");
        if (pf.expect.shouldSolve(pf.file)) {
            assertTrue(name + ": dual chain failed to solve a solvable capture", solved);
            if (target != null) {
                double gap = max ? target - obj : obj - target;
                assertTrue(name + ": objective " + obj + " short of target " + target + " by " + gap,
                        gap <= pf.expect.maxObjectiveGap());
            }
        }
    }
}
