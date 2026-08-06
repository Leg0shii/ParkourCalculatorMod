package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetFile;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetIO;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.experimental.categories.Category;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class GraphPresetSolveTest {

    @Test
    public void savedThenReloadedFastDuplicateSolvesJ001() throws Exception {
        Path dir = Files.createTempDirectory("pkc-graph-solve");
        FileSystemSaveStore store = new FileSystemSaveStore(dir, "test", "1.8.9", null, GraphPresetIO.infoParser());
        GraphPresetFile preset = GraphPresetIO.fromGraph(BuiltinGraphs.fast());
        preset.name = "fast-copy";
        preset.createdAt = SaveIO.nowIso8601();
        preset.modVersion = store.getModVersion();
        store.write("fast-copy", GraphPresetIO.toJson(preset));

        Result<SolverGraph> graph = GraphPresetIO.loadGraph(store, "fast-copy");
        assertTrue(graph.error, graph.ok);

        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("j001"));
        assertNotNull(file);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.CUSTOM);
        state.setStopOnFeasible(true);
        state.setGraphPresetName("fast-copy");
        state.setCustomGraph(graph.value);
        state.clearResult();

        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs,
                t -> { }, ExactJumpModel.forMcVersion(file.mcVersion));
        engine.solve();
        long deadline = System.currentTimeMillis() + 50_000;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            Thread.sleep(2);
        }
        engine.poll();

        SolveResult result = state.getResult();
        assertNotNull("solve did not finish in time", result);
        assertTrue("expected a successful solve, got: " + result.getSolver(), result.isSuccess());
    }
}
