package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.Expect;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Legal mode on the capture. On a landable capture ({@code shouldSolve}) the legal solve lands at the goal
 *  wall with a non-positive shortfall and meets every wall, and normal mode lands too; on an unlandable one
 *  every hard wall holds, only the dropped goal wall is unmet, and the reported shortfall sits in
 *  [{@code minShortfall}, {@code maxShortfall}]. {@code goalWall} pins the wall the shortfall names,
 *  {@code goalWallRhs} the wall the objective must reach, {@code starveSeedMs} repeats the solves with the Fast
 *  graph's seed stage starved to that budget, {@code savedLegalMode} asserts the save round-tripped legal mode,
 *  {@code deterministic} solves twice and compares the shortfall bit for bit. */
final class LegalCheck implements ProblemCheck {

    private static final double FEAS_TOL = 0.0;

    @Override
    public void run(ProblemFixture pf) {
        Expect e = pf.expect;
        boolean shouldSolve = e.shouldSolve(pf.file);
        if (e.flag(e.savedLegalMode, false)) {
            assertTrue(pf.name + ": legalMode must round-trip through SaveIO", pf.rig().state.isLegalMode());
        }
        SolveResult legal = solve(pf, true, null);
        print(pf.name, "legal=on", legal);
        double shortfall = assertLegal(pf, legal, shouldSolve);
        if (shouldSolve) {
            SolveResult normal = solve(pf, false, null);
            print(pf.name, "legal=off", normal);
            assertNormalLands(pf, normal);
        }
        if (e.starveSeedMs != null) {
            SolveResult starved = solve(pf, true, seedStarvedFast(e.starveSeedMs));
            print(pf.name, "legal=on seedStarved", starved);
            assertLegal(pf, starved, shouldSolve);
            if (shouldSolve) {
                SolveResult normal = solve(pf, false, seedStarvedFast(e.starveSeedMs));
                print(pf.name, "legal=off seedStarved", normal);
                assertNormalLands(pf, normal);
            }
        }
        if (e.flag(e.deterministic, false)) {
            SolveResult again = solve(pf, true, null);
            double s2 = assertLegal(pf, again, shouldSolve);
            assertEquals(pf.name + ": legal solve must be deterministic",
                    Double.doubleToRawLongBits(shortfall), Double.doubleToRawLongBits(s2));
        }
    }

    private static double assertLegal(ProblemFixture pf, SolveResult r, boolean shouldSolve) {
        Expect e = pf.expect;
        String name = pf.name;
        assertNotNull(name + ": engine returned no result", r);
        assertTrue(name + ": legal solver name carries the suffix: " + r.getSolver(),
                r.getSolver() != null && r.getSolver().contains("(legal)"));
        String detail = Paths.detail(r, "Legal shortfall");
        assertNotNull(name + ": legal result reports the shortfall", detail);
        if (e.goalWall != null) assertTrue(name + ": shortfall names " + e.goalWall + ": " + detail, detail.contains(e.goalWall));
        double shortfall = Double.parseDouble(detail.split(" ")[0]);
        if (shouldSolve) {
            assertTrue(name + ": legal mode must solve this capture: " + r.getSolver(), r.isSuccess());
            assertTrue(name + ": legal mode must have an objective", r.hasObjective());
            JumpSpec spec = pf.specFor(null, null);
            double wall = e.goalWallRhs != null ? e.goalWallRhs : goalWallRhs(name, spec);
            double miss = Paths.shortfall(spec.objective.sense, r.getObjectiveValue(), wall);
            assertTrue(String.format(Locale.ROOT, "%s: legal mode must reach the goal wall %.9f, achieved %.9f",
                    name, wall, r.getObjectiveValue()), miss <= FEAS_TOL);
            assertEquals(name + ": legal mode meets every wall including the goal wall", r.getTotal(), r.getMet());
            assertTrue(name + ": shortfall is not positive once the wall is reached: " + detail, shortfall <= FEAS_TOL);
        } else {
            assertEquals(name + ": every hard wall must hold; only the dropped goal wall may be unmet", 1, Paths.unmet(r));
            if (e.minShortfall != null) assertTrue(name + ": shortfall " + shortfall + " under " + e.minShortfall, shortfall > e.minShortfall);
            if (e.maxShortfall != null) assertTrue(name + ": shortfall " + shortfall + " over " + e.maxShortfall, shortfall < e.maxShortfall);
        }
        return shortfall;
    }

    private static double goalWallRhs(String name, JumpSpec spec) {
        String[] whyNot = new String[1];
        JumpConstraint goal = AngleSolverEngine.selectLegalGoalWall(spec.constraints, spec.objective, whyNot);
        assertNotNull(name + ": no legal goal wall: " + whyNot[0], goal);
        return goal.rhs;
    }

    private static void assertNormalLands(ProblemFixture pf, SolveResult normal) {
        assertNotNull(pf.name + ": normal mode returned no result", normal);
        assertTrue(pf.name + ": normal mode must solve this capture", normal.isSuccess());
        assertEquals(pf.name + ": normal mode meets every wall", normal.getTotal(), normal.getMet());
    }

    private static SolverGraph seedStarvedFast(int budgetMs) {
        SolverGraph graph = BuiltinGraphs.fast();
        graph.node("seed").params.set("budgetMs", budgetMs);
        return graph;
    }

    private static SolveResult solve(ProblemFixture pf, boolean legalMode, SolverGraph graph) {
        ProblemFixture.Rig rig = pf.rig();
        rig.state.setLegalMode(legalMode);
        rig.state.clearResult();
        long timeout = pf.expect.timeoutMs(60_000L);
        if (graph != null) return rig.solve(AngleSolverState.Effort.FAST, graph, timeout).result;
        return rig.solve(timeout).result;
    }

    private static void print(String name, String label, SolveResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("LEGAL ").append(name).append(" [").append(label).append("] ");
        if (r == null) {
            sb.append("result=null");
            System.out.println(sb);
            return;
        }
        sb.append("success=").append(r.isSuccess())
                .append(" met=").append(r.getMet()).append('/').append(r.getTotal())
                .append(" solver=").append(r.getSolver())
                .append(" objective=").append(r.hasObjective() ? Double.toString(r.getObjectiveValue()) : "none")
                .append(" ms=").append(r.getDurationMs())
                .append(" notice=").append(r.getNotice());
        for (SolveResult.Detail d : r.getDetails()) {
            sb.append("\n    detail ").append(d.label).append(" = ").append(d.value);
        }
        System.out.println(sb);
    }
}
