package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetFile;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetIO;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assume.assumeTrue;

public class RunUpSweepNodeScreen {

    @Test
    public void solve() throws Exception {
        String saveFile = System.getenv("PKC_SOLVE_FILE");
        assumeTrue("PKC_SOLVE_FILE unset; skipping", saveFile != null && !saveFile.isEmpty());
        String graphFile = System.getenv("PKC_GRAPH_FILE");
        int optSec = envInt("PKC_OPTIMIZE_SECONDS", 60);
        int startTick = envInt("PKC_START_TICK", 0);
        int landingTick = envInt("PKC_LANDING_TICK", 42);
        long timeoutMs = optSec * 1000L + 120_000L;

        String saveContents = new String(Files.readAllBytes(Paths.get(saveFile)), StandardCharsets.UTF_8);
        SaveFile file = SaveIO.parseSafe(saveContents);
        if (file == null) throw new IllegalStateException("parse failed: " + saveFile);

        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setStartTick(startTick);
        state.setLandingTick(landingTick);
        state.setOptimizeSeconds(optSec);
        state.clearResult();

        SolverGraph graph = null;
        if (graphFile != null && !graphFile.isEmpty()) {
            String graphContents = new String(Files.readAllBytes(Paths.get(graphFile)), StandardCharsets.UTF_8);
            Result<GraphPresetFile> parsed = GraphPresetIO.parse(graphContents);
            if (!parsed.ok) throw new IllegalStateException("graph parse failed: " + parsed.error);
            Result<SolverGraph> mat = GraphPresetIO.materialize(parsed.value);
            if (!mat.ok) throw new IllegalStateException("graph materialize failed: " + mat.error);
            graph = mat.value;
            state.setEffort(AngleSolverState.Effort.CUSTOM);
            state.setGraphPresetName(graph.name);
            state.setCustomGraph(graph);
        }

        BoxController boxes = Fixtures.buildBoxes(file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, model);

        System.out.printf("SWEEP setup file=%s graph=%s optSec=%d startTick=%d landingTick=%d mc=%s%n",
                saveFile, graphFile, optSec, startTick, landingTick, file.mcVersion);

        long t0 = System.nanoTime();
        engine.solve();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            Thread.sleep(2);
        }
        engine.poll();
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        SolveResult r = state.getResult();
        boolean success = r != null && r.isSuccess();
        double obj = r != null && r.hasObjective() ? r.getObjectiveValue() : Double.NaN;
        String solver = r != null && r.getSolver() != null ? r.getSolver() : "-";

        SolveRunRecord rec = engine.lastRunRecord();
        String chain = rec != null && rec.outcome != null ? rec.outcome.chain : null;
        Double recObj = rec != null && rec.outcome != null ? rec.outcome.objective : null;

        System.out.printf("SWEEP result success=%s obj=%.9f solver=%s ms=%d%n", success, obj, solver, ms);
        System.out.printf("SWEEP chain=%s%n", chain);
        System.out.printf("SWEEP recordObjective=%s feasible=%s%n",
                recObj, rec != null && rec.outcome != null ? rec.outcome.feasible : null);
        if (rec != null && rec.nodes != null) {
            for (SolveRunRecord.NodeRun nr : rec.nodes) {
                System.out.printf("RECNODE id=%-14s label=%-20s ms=%-7d taken=%s%n",
                        nr.id, nr.label, nr.elapsedNanos / 1_000_000L, nr.taken);
            }
        }
        double offset = -699.95 - obj;
        System.out.printf("SWEEP offset(-699.95 - obj)=%.9f  (accept >= 0.000268)%n", offset);
    }

    private static int envInt(String key, int def) {
        String v = System.getenv(key);
        if (v == null || v.isEmpty()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
