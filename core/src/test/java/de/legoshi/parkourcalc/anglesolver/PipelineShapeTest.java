package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.VerySlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Category(VerySlowSolverTests.class)
public class PipelineShapeTest {

    private static final List<String> FAST_PIPELINE = Arrays.asList(
            "seed", "horizon", "wrap0", "cap1", "freeRescue", "peel", "freeImprove", "sweep",
            "fold", "ladder", "cert", "bnb", "ils", "cap2", "wrap", "translate");

    private static final List<String> THOROUGH_PIPELINE = Arrays.asList(
            "horizon", "wrap0", "seed", "cap1", "freeRescue", "peel", "freeImprove", "sweep",
            "fold", "ladder", "cert", "bnb", "ils", "cap2", "wrap", "translate", "snap");

    private static final long TIMEOUT_MS = 4000;

    @Test
    public void everyCaptureVisitsItsTierStageSequence() throws Exception {
        List<File> files = new ArrayList<>();
        collect(resolve("/captures"), files);
        collect(resolve("/problems/solve"), files);
        collect(resolve("/problems/closedform"), files);
        assertTrue("no corpus captures found", files.size() > 50);

        Set<String> fastShapes = new LinkedHashSet<>();
        Set<String> thoroughShapes = new LinkedHashSet<>();
        int solved = 0;
        int cancelled = 0;
        int skipped = 0;
        for (File f : files) {
            SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            if (file == null || file.angleSolver == null || file.angleSolver.seed == null
                    || file.rows == null || file.rows.isEmpty()) {
                skipped++;
                continue;
            }
            for (boolean thorough : new boolean[]{false, true}) {
                List<String> pipeline = thorough ? THOROUGH_PIPELINE : FAST_PIPELINE;
                Set<String> shapes = thorough ? thoroughShapes : fastShapes;
                String tierName = thorough ? "THOROUGH" : "FAST";
                List<String> seq = runOnce(f.getName(), file, thorough, pipeline);
                if (seq == null) {
                    skipped++;
                    continue;
                }
                boolean complete = seq.size() == pipeline.size();
                if (complete) {
                    solved++;
                    shapes.add(String.join(">", seq));
                    if (shapes.size() > 1) {
                        fail("second distinct stage sequence at " + f.getName() + " tier=" + tierName
                                + ": " + seq + " vs " + shapes.iterator().next());
                    }
                } else {
                    cancelled++;
                    assertEquals("truncated run is not a pipeline prefix at " + f.getName() + " tier=" + tierName,
                            pipeline.subList(0, seq.size()), seq);
                }
            }
        }
        System.out.printf("PIPELINE SHAPE complete=%d truncated=%d skipped=%d fastDistinct=%d thoroughDistinct=%d%n",
                solved, cancelled, skipped, fastShapes.size(), thoroughShapes.size());
        assertEquals("distinct complete FAST stage sequences", 1, fastShapes.size());
        assertEquals(String.join(">", FAST_PIPELINE), fastShapes.iterator().next());
        assertEquals("distinct complete THOROUGH stage sequences", 1, thoroughShapes.size());
        assertEquals(String.join(">", THOROUGH_PIPELINE), thoroughShapes.iterator().next());
    }

    private List<String> runOnce(String name, SaveFile file, boolean thorough, List<String> pipeline) {
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        if (thorough) {
            state.setEffort(AngleSolverState.Effort.THOROUGH);
            state.setOptimizeSeconds(1);
        } else {
            state.setEffort(AngleSolverState.Effort.FAST);
        }
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        engine.solve();
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        boolean timedOut = false;
        while (engine.isSolving()) {
            engine.poll();
            if (System.currentTimeMillis() >= deadline) {
                timedOut = true;
                engine.cancel();
                break;
            }
            sleep(1);
        }
        engine.poll();
        GraphRunState rs = engine.graphRunState();
        if (rs == null) return null;
        List<String> raw = new ArrayList<>();
        for (GraphRunState.Step s : rs.steps()) raw.add(s.nodeId);
        List<String> seq = collapseLoop(raw);
        if (!timedOut && seq.size() != pipeline.size()) {
            fail("incomplete sequence without timeout at " + name + " tier=" + (thorough ? "THOROUGH" : "FAST")
                    + ": " + seq);
        }
        return seq;
    }

    private static List<String> collapseLoop(List<String> seq) {
        List<String> out = new ArrayList<>();
        for (String id : seq) {
            String mapped = id.equals("sweep") || id.equals("ils2") || id.equals("translate2") ? "sweep" : id;
            if (!out.isEmpty() && out.get(out.size() - 1).equals(mapped)) continue;
            out.add(mapped);
        }
        return out;
    }

    private static File resolve(String path) throws Exception {
        URL url = PipelineShapeTest.class.getResource(path);
        if (url == null) throw new IllegalStateException("missing " + path);
        return new File(url.toURI());
    }

    private static void collect(File dir, List<File> out) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        Arrays.sort(entries);
        for (File e : entries) {
            if (e.isDirectory()) collect(e, out);
            else if (e.getName().endsWith(".json") && !e.getName().endsWith(".expect.json")) out.add(e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
