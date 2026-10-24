package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.Expect;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The live engine reaches the expected verdict (optionally every Solve-For direction) within a time budget.
 *  Sidecar extras: {@code noticeLabel} / {@code noticeContains} pin the failure notice of a capture that must
 *  not solve, {@code maxRecomputeDivergence} bounds how far a naive re-simulation of the result yaws may sit
 *  from the shipped objective, {@code reproducibleWithin} solves twice and bounds the objective drift, and
 *  {@code boxSeeds} re-solves from the corners, the centre and an off-centre point of the start box. */
final class SolveCheck implements ProblemCheck {

    @Override
    public void run(ProblemFixture pf) {
        if (pf.expect.allDirections()) {
            runAllDirections(pf);
            return;
        }
        Expect e = pf.expect;
        String name = pf.name;
        ProblemFixture.Run run = pf.solve(timeout(pf));
        SolveResult r = run.result;
        assertNotNull(name + ": no result after solve", r);
        System.out.printf("SOLVE %-22s success=%s met=%d/%d  %d ms%s%n",
                name, r.isSuccess(), r.getMet(), r.getTotal(), run.elapsedMs,
                r.hasObjective() ? String.format("  obj=%.7f", r.getObjectiveValue()) : "");

        if (e.shouldSolve(pf.file)) {
            assertTrue(name + ": solver met " + r.getMet() + "/" + r.getTotal() + " constraints", r.isSuccess());
        } else {
            assertFalse(name + ": expected NOT to solve but it did", r.isSuccess());
        }
        if (e.minMet != null) {
            assertTrue(name + ": met " + r.getMet() + " < required " + e.minMet, r.getMet() >= e.minMet);
        }
        if (e.refObjective != null) {
            assertTrue(name + ": result carries no objective", r.hasObjective());
            boolean max = "MAX".equals(pf.file.angleSolver.goal);
            double gap = max ? e.refObjective - r.getObjectiveValue() : r.getObjectiveValue() - e.refObjective;
            assertTrue(name + ": objective " + r.getObjectiveValue() + " short of reference "
                    + e.refObjective + " by " + gap, gap <= e.maxObjectiveGap());
        }
        if (e.maxSolveMs != null) {
            assertTrue(name + ": solve took " + run.elapsedMs + " ms > budget " + e.maxSolveMs + " ms",
                    run.elapsedMs <= e.maxSolveMs);
        }
        if (e.noticeLabel != null) {
            assertNotNull(name + ": failure carries no notice", r.getNotice());
            assertEquals(name + ": notice label", e.noticeLabel, r.getNoticeLabel());
        }
        if (e.noticeContains != null) {
            assertNotNull(name + ": failure carries no notice", r.getNotice());
            for (String part : e.noticeContains) {
                assertTrue(name + ": notice lacks '" + part + "': " + r.getNotice(), r.getNotice().contains(part));
            }
        }
        if (e.maxRecomputeDivergence != null) {
            assertTrue(name + ": result carries no objective", r.hasObjective());
            JumpSpec spec = run.engine.lastSpecDebug();
            assertNotNull(name + ": engine kept no spec", spec);
            JumpPhysicsInputs sc = spec.asScenario();
            double[] gf = Paths.gameFacings(sc, Paths.resultYaws(r));
            ForwardPath path = pf.model.forward(sc, gf);
            double recompute = spec.objective.evaluate(path);
            double div = Math.abs(r.getObjectiveValue() - recompute);
            System.out.printf("SOLVE %-22s recompute=%.9f div=%.2e%n", name, recompute, div);
            assertTrue(name + ": shipped objective " + r.getObjectiveValue() + " diverges from the re-simulated "
                    + recompute + " by " + div, div < e.maxRecomputeDivergence);
        }
        if (e.reproducibleWithin != null) {
            assertTrue(name + ": result carries no objective", r.hasObjective());
            ProblemFixture.Run again = pf.solve(timeout(pf));
            assertNotNull(name + ": no result on the repeat solve", again.result);
            assertTrue(name + ": repeat solve carries no objective", again.result.hasObjective());
            double drift = Math.abs(r.getObjectiveValue() - again.result.getObjectiveValue());
            assertTrue(name + ": objective not reproducible within " + e.reproducibleWithin + " ("
                    + r.getObjectiveValue() + " vs " + again.result.getObjectiveValue() + ")", drift < e.reproducibleWithin);
        }
        if (e.flag(e.boxSeeds, false)) runBoxSeeds(pf);
    }

    static long timeout(ProblemFixture pf) {
        Expect e = pf.expect;
        long fallback = 60_000L;
        if (e.maxSolveMs != null) fallback = Math.max(e.maxSolveMs * 3, 10_000L);
        if (e.optimizeSeconds != null) fallback = Math.max(fallback, 2_000L * e.optimizeSeconds);
        return e.timeoutMs(fallback);
    }

    /** Whether a solution exists is a property of the constraints, not the optimized direction, so every
     *  Solve-For (axis x goal) must find one on a solvable problem. */
    private void runAllDirections(ProblemFixture pf) {
        for (AngleSolverState.Axis axis : AngleSolverState.Axis.values()) {
            for (AngleSolverState.Goal goal : AngleSolverState.Goal.values()) {
                ProblemFixture.Run run = pf.solveDirectedFeasible(60_000L, axis, goal);
                SolveResult r = run.result;
                String dir = axis + "/" + goal;
                assertNotNull(pf.name + " " + dir + ": engine returned no result", r);
                System.out.printf("SOLVE %-22s %-7s success=%s met=%d/%d  %d ms%n",
                        pf.name, dir, r.isSuccess(), r.getMet(), r.getTotal(), run.elapsedMs);
                assertTrue(pf.name + " " + dir + ": no solution (" + r.getMet() + "/" + r.getTotal()
                        + " met); the problem is solvable, so every Solve-For must find one", r.isSuccess());
            }
        }
    }

    /** The solve must not depend on where in the start box the seed sits: the four corners, the centre and an
     *  off-centre point all land. */
    private void runBoxSeeds(ProblemFixture pf) {
        double[] box = tickStartBox(pf.file);
        double xLo = box[0], xHi = box[1], zLo = box[2], zHi = box[3];
        double cx = 0.5 * (xLo + xHi), cz = 0.5 * (zLo + zHi);
        double jx = xLo + 0.37 * (xHi - xLo), jz = zLo + 0.62 * (zHi - zLo);
        double[][] seeds = {
                {xLo, zLo}, {xLo, zHi}, {xHi, zLo}, {xHi, zHi},
                {cx, cz}, {jx, jz},
        };
        double[] savedSeed = pf.file.angleSolver.seed.pos;
        double[] savedStart = pf.file.start != null ? pf.file.start.pos : null;
        try {
            for (double[] s : seeds) {
                applySeed(pf.file, s[0], s[1]);
                ProblemFixture.Run run = pf.solve(timeout(pf));
                SolveResult r = run.result;
                String where = pf.name + " seed (" + s[0] + "," + s[1] + ")";
                System.out.printf("SOLVE %-22s success=%s  %d ms%n", where, r != null && r.isSuccess(), run.elapsedMs);
                assertTrue(where + ": FAST found no feasible solution", r != null && r.isSuccess() && r.hasObjective());
            }
        } finally {
            pf.file.angleSolver.seed.pos = savedSeed;
            if (pf.file.start != null) pf.file.start.pos = savedStart;
        }
    }

    private static void applySeed(SaveFile file, double x, double z) {
        double y = file.angleSolver.seed.pos[1];
        file.angleSolver.seed.pos = new double[]{x, y, z};
        if (file.start != null && file.start.pos != null && file.start.pos.length >= 3) {
            file.start.pos = new double[]{x, file.start.pos[1], z};
        }
    }

    private static double[] tickStartBox(SaveFile file) {
        int startTick = file.angleSolver.startTick;
        double xLo = Double.NaN, xHi = Double.NaN, zLo = Double.NaN, zHi = Double.NaN;
        for (SaveFile.Tick t : file.angleSolver.ticks) {
            if (t.tick != startTick) continue;
            for (SaveFile.Constraint c : t.constraints) {
                if (!c.range) continue;
                if ("X".equals(c.field)) {
                    xLo = c.lo;
                    xHi = c.hi;
                } else if ("Z".equals(c.field)) {
                    zLo = c.lo;
                    zHi = c.hi;
                }
            }
        }
        if (Double.isNaN(xLo) || Double.isNaN(zLo)) {
            throw new IllegalStateException("no tick-" + startTick + " X/Z range box in fixture");
        }
        return new double[]{xLo, xHi, zLo, zHi};
    }
}
