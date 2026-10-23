package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.Expect;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The {@code Fast (multi-start)} preset (seed sweep, best-of-N parallel Fast over the start box) against a
 *  single Fast. Both must land. {@code seedsTaken: "FOUND"} says the sweep must adopt a candidate on this
 *  free-start capture, and then the multi-start result is never worse than Fast and reaches
 *  {@code refObjective} (the in-game basin); {@code seedsTaken: "NONE"} says a pinned start passes through the
 *  sweep instantly (under 200 ms) and the solver chain never names it. */
final class MultiStartCheck implements ProblemCheck {

    @Override
    public void run(ProblemFixture pf) {
        Expect e = pf.expect;
        Objective.Sense sense = pf.specFor(null, null).objective.sense;
        long timeout = e.timeoutMs(60_000L);

        ProblemFixture.Rig fastRig = pf.rig();
        SolveResult fast = fastRig.solve(AngleSolverState.Effort.FAST, null, timeout).result;
        assertLanded(pf.name + " fast", fast);

        ProblemFixture.Rig multiRig = pf.rig();
        multiRig.state.setGraphPresetName(BuiltinGraphs.MULTI_START_PRESET);
        SolveResult multi = multiRig.solve(AngleSolverState.Effort.CUSTOM, BuiltinGraphs.fastMultiStart(), 2 * timeout).result;
        assertLanded(pf.name + " multi-start", multi);
        System.out.printf(Locale.ROOT, "MULTISTART %-24s fast obj=%.9f (%d ms)  multi obj=%.9f (%d ms) solver=%s%n",
                pf.name, fast.getObjectiveValue(), fast.getDurationMs(), multi.getObjectiveValue(), multi.getDurationMs(),
                multi.getSolver());

        SolveRunRecord rec = multiRig.engine.lastRunRecord();
        assertNotNull(pf.name + ": no run record", rec);
        SolveRunRecord.NodeRun seeds = null;
        for (SolveRunRecord.NodeRun n : rec.nodes) {
            if ("seeds".equals(n.id)) seeds = n;
        }
        assertNotNull(pf.name + ": seed sweep node missing from the run", seeds);
        if (e.seedsTaken != null) assertEquals(pf.name + ": seed sweep outcome", e.seedsTaken, seeds.taken);

        if ("FOUND".equals(seeds.taken)) {
            assertTrue(pf.name + ": multi-start " + multi.getObjectiveValue() + " is worse than fast " + fast.getObjectiveValue(),
                    Paths.shortfall(sense, multi.getObjectiveValue(), fast.getObjectiveValue()) <= 0.0);
            if (e.refObjective != null) {
                assertTrue(pf.name + ": multi-start " + multi.getObjectiveValue() + " did not reach the in-game fast basin "
                        + e.refObjective, Paths.shortfall(sense, multi.getObjectiveValue(), e.refObjective) <= 1.0e-6);
            }
        } else if ("NONE".equals(seeds.taken)) {
            assertTrue(pf.name + ": pinned sweep should be instant, took " + seeds.elapsedNanos / 1_000_000L + " ms",
                    seeds.elapsedNanos < 200_000_000L);
            assertTrue(pf.name + ": the solver chain names the sweep: " + multi.getSolver(),
                    multi.getSolver() == null || !multi.getSolver().contains("seed sweep"));
        }
    }

    private static void assertLanded(String label, SolveResult r) {
        assertNotNull(label + ": no result", r);
        assertTrue(label + ": no solution: " + r.getSolver(), r.isSuccess());
        assertTrue(label + ": no objective", r.hasObjective());
    }
}
