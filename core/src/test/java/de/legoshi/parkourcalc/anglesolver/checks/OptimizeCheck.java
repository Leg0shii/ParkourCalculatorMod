package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.Expect;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.Locale;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Optimize is never worse than Fast. {@code mode: "cold"} (default) solves Fast and Optimize on two fresh
 *  engines, requires both to land, Optimize's objective to be at least Fast's, and the run record to publish at
 *  least {@code minIncumbents} incumbents (so the live panel moves and Cancel keeps the best so far).
 *  {@code mode: "incumbent"} solves Fast and then Optimize on the same engine, the way a player presses
 *  Optimize after Fast: the chain must start from the incumbent and never end worse than it.
 *  {@code optimizeSeconds} is the Optimize budget. */
final class OptimizeCheck implements ProblemCheck {

    @Override
    public void run(ProblemFixture pf) {
        Expect e = pf.expect;
        String mode = e.mode != null ? e.mode : "cold";
        Objective.Sense sense = pf.specFor(null, null).objective.sense;
        long timeout = e.timeoutMs(60_000L);
        if ("incumbent".equals(mode)) {
            int seconds = e.or(e.optimizeSeconds, 8);
            ProblemFixture.Rig rig = pf.rig();
            SolveResult fast = rig.solve(AngleSolverState.Effort.FAST, null, timeout).result;
            assertLanded(pf.name + " fast", fast);
            rig.state.setOptimizeSeconds(seconds);
            SolveResult opt = rig.solve(AngleSolverState.Effort.THOROUGH, null, timeout).result;
            assertLanded(pf.name + " optimize", opt);
            System.out.printf(Locale.ROOT, "OPTIMIZE %-24s fast obj=%.9f  optimize obj=%.9f solver=%s%n",
                    pf.name, fast.getObjectiveValue(), opt.getObjectiveValue(), opt.getSolver());
            assertTrue(pf.name + ": optimize solver chain does not start from the incumbent: " + opt.getSolver(),
                    opt.getSolver() != null && opt.getSolver().startsWith("incumbent"));
            assertTrue(pf.name + ": optimize " + opt.getObjectiveValue() + " is worse than the fast incumbent "
                    + fast.getObjectiveValue(), Paths.shortfall(sense, opt.getObjectiveValue(), fast.getObjectiveValue()) <= 1.0e-9);
            return;
        }
        int seconds = e.or(e.optimizeSeconds, 10);
        ProblemFixture.Rig fastRig = pf.rig();
        fastRig.state.setEffort(AngleSolverState.Effort.FAST);
        fastRig.state.setOptimizeSeconds(seconds);
        SolveResult fast = fastRig.solve(timeout).result;
        ProblemFixture.Rig optRig = pf.rig();
        optRig.state.setEffort(AngleSolverState.Effort.THOROUGH);
        optRig.state.setOptimizeSeconds(seconds);
        SolveResult opt = optRig.solve(timeout).result;
        SolveRunRecord record = optRig.engine.lastRunRecord();

        assertLanded(pf.name + " fast", fast);
        assertLanded(pf.name + " optimize", opt);
        System.out.printf(Locale.ROOT, "OPTIMIZE %-24s fast obj=%.7f  optimize obj=%.7f  samples=%d%n",
                pf.name, fast.getObjectiveValue(), opt.getObjectiveValue(),
                record == null ? -1 : record.trajectory.size());
        assertTrue(pf.name + ": optimize objective " + opt.getObjectiveValue() + " is worse than fast's "
                + fast.getObjectiveValue(), Paths.shortfall(sense, opt.getObjectiveValue(), fast.getObjectiveValue()) <= 0.0);
        assertNotNull(pf.name + ": optimize kept no run record", record);
        int minIncumbents = e.or(e.minIncumbents, 2);
        assertTrue(pf.name + ": optimize published " + record.trajectory.size()
                + " incumbents; Cancel would keep the first candidate", record.trajectory.size() >= minIncumbents);
    }

    private static void assertLanded(String label, SolveResult r) {
        assertNotNull(label + ": no result", r);
        assertTrue(label + ": no solution: " + r.getSolver(), r.isSuccess());
        assertTrue(label + ": no objective", r.hasObjective());
    }
}
