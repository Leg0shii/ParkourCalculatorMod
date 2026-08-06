package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemCatalog;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import org.junit.experimental.categories.Category;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The capture-driven suite: one parameterized run per file under {@code resources/problems/<check>/}. The
 * folder name is the check; each capture's optional {@code .expect.json} tunes it. Add a capture by dropping
 * it (or a sidecar pointing at the {@code /captures/} library) into a folder; no Java change.
 *
 * <ul>
 *   <li>{@code solve/} - the live engine reaches the expected verdict (optionally every Solve-For direction),
 *       within an optional time budget</li>
 *   <li>{@code closedform/} - the closed-form solve is byte-exact feasible, on objective, and fast</li>
 *   <li>{@code dualrecovery/} - the deterministic dual chain (closed form, SLP, reseeded SLP, relaxation
 *       recovery) byte-exact-solves the capture in its saved direction, no CMA-ES, no warm start</li>
 * </ul>
 */
@RunWith(Parameterized.class)
@Category(SlowSolverTests.class)
public class ProblemsTest {

    @Parameters(name = "{0}/{1}")
    public static Collection<Object[]> problems() {
        Collection<Object[]> out = new ArrayList<>();
        for (String category : ProblemCatalog.categories()) {
            for (String name : ProblemCatalog.problemNames(category)) out.add(new Object[]{category, name});
        }
        return out;
    }

    @Parameter(0)
    public String category;

    @Parameter(1)
    public String name;

    @Test
    public void check() {
        ProblemFixture pf = ProblemFixture.load(category, name);
        switch (pf.expect.check(category)) {
            case "solve":        runSolve(pf);        break;
            case "closedform":   runClosedForm(pf);   break;
            case "dualrecovery": runDualRecovery(pf); break;
            default:             fail(name + ": unknown check '" + pf.expect.check(category) + "'");
        }
    }

    private void runSolve(ProblemFixture pf) {
        if (pf.expect.allDirections()) {
            runAllDirections(pf);
            return;
        }
        long timeout = pf.expect.maxSolveMs != null ? Math.max(pf.expect.maxSolveMs * 3, 10_000L) : 60_000L;
        ProblemFixture.Run run = pf.solve(timeout);
        SolveResult r = run.result;
        assertNotNull(name + ": no result after solve", r);
        System.out.printf("SOLVE %-22s success=%s met=%d/%d  %d ms%s%n",
                name, r.isSuccess(), r.getMet(), r.getTotal(), run.elapsedMs,
                r.hasObjective() ? String.format("  obj=%.7f", r.getObjectiveValue()) : "");

        if (pf.expect.shouldSolve(pf.file)) {
            assertTrue(name + ": solver met " + r.getMet() + "/" + r.getTotal() + " constraints", r.isSuccess());
        } else {
            assertFalse(name + ": expected NOT to solve but it did", r.isSuccess());
        }
        if (pf.expect.minMet != null) {
            assertTrue(name + ": met " + r.getMet() + " < required " + pf.expect.minMet,
                    r.getMet() >= pf.expect.minMet);
        }
        if (pf.expect.refObjective != null) {
            assertTrue(name + ": result carries no objective", r.hasObjective());
            boolean max = "MAX".equals(pf.file.angleSolver.goal);
            double gap = max ? pf.expect.refObjective - r.getObjectiveValue()
                             : r.getObjectiveValue() - pf.expect.refObjective; // > 0: short of the reference
            assertTrue(name + ": objective " + r.getObjectiveValue() + " short of reference "
                    + pf.expect.refObjective + " by " + gap, gap <= pf.expect.maxObjectiveGap());
        }
        if (pf.expect.maxSolveMs != null) {
            assertTrue(name + ": solve took " + run.elapsedMs + " ms > budget " + pf.expect.maxSolveMs + " ms",
                    run.elapsedMs <= pf.expect.maxSolveMs);
        }
    }

    /** Whether a solution exists is a property of the constraints, not the optimized direction, so every
     *  Solve-For (axis x goal) must find one on a solvable problem. */
    private void runAllDirections(ProblemFixture pf) {
        for (AngleSolverState.Axis axis : AngleSolverState.Axis.values()) {
            for (AngleSolverState.Goal goal : AngleSolverState.Goal.values()) {
                ProblemFixture.Run run = pf.solveDirectedFeasible(60_000L, axis, goal);
                SolveResult r = run.result;
                String dir = axis + "/" + goal;
                assertNotNull(name + " " + dir + ": engine returned no result", r);
                System.out.printf("SOLVE %-22s %-7s success=%s met=%d/%d  %d ms%n",
                        name, dir, r.isSuccess(), r.getMet(), r.getTotal(), run.elapsedMs);
                assertTrue(name + " " + dir + ": no solution (" + r.getMet() + "/" + r.getTotal()
                        + " met); the problem is solvable, so every Solve-For must find one", r.isSuccess());
            }
        }
    }

    private void runDualRecovery(ProblemFixture pf) {
        ExactJumpModel exact = pf.model;
        JumpSpec spec = pf.specFor(null, null);
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);

        AtomicBoolean cancel = new AtomicBoolean(false);
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        Double target = pf.expect.refObjective;
        long t0 = System.nanoTime();
        String[] chain = new String[1];
        double[] yaws = de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine.dualChain(exact, spec, sc, cancel, chain);
        boolean shortOfTarget = false;
        if (yaws != null && target != null) {
            double o = objectiveOf(exact, sc, spec, yaws);
            shortOfTarget = (max ? target - o : o - target) > pf.expect.maxObjectiveGap();
        }
        if ((yaws == null || shortOfTarget) && pf.expect.bnbSeconds != null) {
            double stopAt = target != null ? target : (max ? -1.0e300 : 1.0e300);
            double[] bnb = de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery.solve(exact, spec, 0.0,
                    cancel, pf.expect.bnbSeconds * 1_000_000_000L, stopAt);
            if (bnb != null && (yaws == null || (max ? objectiveOf(exact, sc, spec, bnb) > objectiveOf(exact, sc, spec, yaws)
                                                     : objectiveOf(exact, sc, spec, bnb) < objectiveOf(exact, sc, spec, yaws)))) {
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

    private static double objectiveOf(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec, double[] yawsAbs) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        return exact.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
    }

    private void runClosedForm(ProblemFixture pf) {
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
        double objGap = max ? refObj - obj : obj - refObj; // > 0 means closed form is worse than reference
        assertTrue(name + ": objective regressed vs reference by " + objGap, objGap <= pf.expect.maxObjectiveGap());

        for (int i = 0; i < 200; i++) ClosedFormSolve.optimize(exact, spec, 0.0, cancel); // warm up the JIT
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
    private double referenceObjective(ProblemFixture pf) {
        if (pf.expect.refObjective != null) return pf.expect.refObjective;
        if (pf.file.angleSolver.result != null) return pf.file.angleSolver.result.objectiveValue;
        throw new IllegalStateException(name + ": closedform needs \"refObjective\" in its .expect.json");
    }
}
