package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FreeStartRescueTest {

    private static GraphNode nodeById(SolverGraph g, String id) {
        for (GraphNode n : g.nodes) {
            if (n.id.equals(id)) return n;
        }
        return null;
    }

    @Test
    public void builtinGraphsCarryTheEarlyRescueNode() {
        for (SolverGraph g : new SolverGraph[]{BuiltinGraphs.fast(), BuiltinGraphs.optimize(10)}) {
            GraphNode rescue = nodeById(g, "freeRescue");
            assertNotNull(g.name + " must contain the early free-start rescue", rescue);
            assertTrue(rescue.params.getBool("jointOnly"));

        }
    }

    @Test
    public void infeasibleSeedIsRescuedBeforeTheRaces() {
        ProblemFixture pf = ProblemFixture.load("solve", "df-chain-free-start");
        ProblemFixture.Run run = pf.solve(60_000L);
        SolveResult r = run.result;
        assertNotNull("engine returned no result", r);
        assertTrue("free-start dF capture must solve", r.isSuccess());
        assertNotNull("solver label missing", r.getSolver());
        assertTrue("expected the joint start stage to produce the candidate, got: " + r.getSolver(),
                r.getSolver().startsWith("joint start"));
    }

    @Test
    public void theJointStageLandsAcrossInertiaPatterns() throws Exception {
        String[][] jumps = {{"d12", "j154_1bm_Head_Butterfly_Neo"}, {"d10", "j345_3jmm_True_Nix_Neo"}};
        for (String[] jump : jumps) {
            SaveFile file = Fixtures.hpkHuman(jump[0], jump[1]);
            StartBox box = engine(file).debugBuildSpec().asScenario().startBox;
            double x = box.pxLo + 0.1 * (box.pxHi - box.pxLo);
            double z = box.pzLo + 0.1 * (box.pzHi - box.pzLo);
            file.angleSolver.seed.pos = new double[]{x, file.angleSolver.seed.pos[1], z};
            if (file.debug != null && !file.debug.isEmpty()) {
                double[] d = file.debug.get(0).pos;
                file.debug.get(0).pos = new double[]{x, d[1], z};
            }
            AngleSolverState state = new AngleSolverState();
            InputData inputs = new InputData();
            SaveIO.applyRowsTo(file, inputs);
            SaveIO.applyAngleSolverTo(file, state);
            state.setEffort(AngleSolverState.Effort.FAST);
            state.clearResult();
            AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { },
                    ExactJumpModel.forMcVersion(file.mcVersion));
            engine.solve();
            long deadline = System.currentTimeMillis() + 60_000L;
            while (engine.isSolving() && System.currentTimeMillis() < deadline) {
                engine.poll();
                Thread.sleep(1);
            }
            engine.poll();
            SolveResult r = state.getResult();
            assertNotNull(jump[1] + ": engine returned no result", r);
            assertTrue(jump[1] + ": must land", r.isSuccess());
            assertTrue(jump[1] + ": expected the joint start stage to land, got: " + r.getSolver(),
                    r.getSolver().startsWith("joint start"));
        }
    }

    private static AngleSolverEngine engine(SaveFile file) {
        AngleSolverState state = new AngleSolverState();
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        SaveIO.applyAngleSolverTo(file, state);
        return new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { },
                ExactJumpModel.forMcVersion(file.mcVersion));
    }
}