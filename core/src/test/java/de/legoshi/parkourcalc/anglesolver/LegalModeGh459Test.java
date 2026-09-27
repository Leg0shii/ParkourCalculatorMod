package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class LegalModeGh459Test {

    private static final String CAPTURE = "gh459-legal-mode";
    private static final long TIMEOUT_MS = 60_000L;
    private static final double FEAS_TOL = 0.0;

    @Test
    public void legalModeSolvesWhatNormalModeSolves() {
        SaveFile file = load();
        SolveResult normal = solve(file, false, null);
        print("legal=off", normal);
        SolveResult legal = solve(file, true, null);
        print("legal=on", legal);

        assertNormalLands(normal);
        assertLegalReachesGoalWall(file, legal);
    }

    @Test
    public void legalModeStillLandsWhenTheSeedStageRunsOutOfTime() {
        SaveFile file = load();
        SolveResult normal = solve(file, false, seedStarvedFast());
        print("legal=off seedStarved", normal);
        SolveResult legal = solve(file, true, seedStarvedFast());
        print("legal=on seedStarved", legal);

        assertNormalLands(normal);
        assertLegalReachesGoalWall(file, legal);
    }

    private static void assertNormalLands(SolveResult normal) {
        assertNotNull(normal);
        assertTrue("normal mode must solve this capture", normal.isSuccess());
        assertEquals("normal mode meets every wall", normal.getTotal(), normal.getMet());
    }

    private static void assertLegalReachesGoalWall(SaveFile file, SolveResult legal) {
        double goalWallRhs = landingLowerX(file);
        assertNotNull(legal);
        assertTrue("legal mode must solve this capture: " + legal.getSolver(), legal.isSuccess());
        assertTrue("legal solver name carries the suffix: " + legal.getSolver(), legal.getSolver().contains("(legal)"));
        assertTrue("legal mode must have an objective", legal.hasObjective());
        assertTrue(String.format(java.util.Locale.ROOT,
                "legal mode must reach the goal wall X >= %.9f, achieved %.9f",
                goalWallRhs, legal.getObjectiveValue()),
                legal.getObjectiveValue() >= goalWallRhs - FEAS_TOL);
        assertEquals("legal mode meets every wall including the goal wall", legal.getTotal(), legal.getMet());
        String shortfall = null;
        for (SolveResult.Detail d : legal.getDetails()) {
            if ("Legal shortfall".equals(d.label)) shortfall = d.value;
        }
        assertNotNull("legal result reports the shortfall", shortfall);
        assertTrue("shortfall is not positive once the wall is reached: " + shortfall,
                Double.parseDouble(shortfall.split(" ")[0]) <= FEAS_TOL);
    }

    private static SolverGraph seedStarvedFast() {
        SolverGraph graph = BuiltinGraphs.fast();
        graph.node("seed").params.set("budgetMs", 1);
        return graph;
    }

    private static SaveFile load() {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(CAPTURE));
        assertNotNull("capture must parse", file);
        assertNotNull(file.angleSolver);
        assertNotNull(file.angleSolver.seed);
        return file;
    }

    private static SolveResult solve(SaveFile file, boolean legalMode, SolverGraph graph) {
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.FAST);
        state.setLegalMode(legalMode);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { },
                ExactJumpModel.forMcVersion(file.mcVersion));
        if (graph != null) engine.solve(AngleSolverState.Effort.FAST, true, graph);
        else engine.solve();
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            sleep(2);
        }
        engine.poll();
        return state.getResult();
    }

    private static double landingLowerX(SaveFile file) {
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        TickConstraints tc = state.tickConstraintsOrNull(state.getLandingTick());
        assertNotNull("landing tick must carry constraints", tc);
        for (Constraint c : tc.getConstraints()) {
            if (c.getField() == Constraint.Field.X && c.isRange()) return c.getLo();
        }
        throw new AssertionError("landing tick has no X range");
    }

    private static void print(String label, SolveResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("[gh459 ").append(label).append("] ");
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
                .append(" unmetTicks=").append(r.getUnmetTicks())
                .append(" notice=").append(r.getNotice());
        for (SolveResult.Detail d : r.getDetails()) {
            sb.append("\n    detail ").append(d.label).append(" = ").append(d.value);
        }
        for (SolveResult.Outcome o : r.getOutcomes()) {
            sb.append("\n    outcome ").append(o.field).append(' ').append(o.tick).append(' ').append(o.relation)
                    .append(" found=").append(o.found).append(" margin=").append(o.margin)
                    .append(" met=").append(o.met);
        }
        System.out.println(sb);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
