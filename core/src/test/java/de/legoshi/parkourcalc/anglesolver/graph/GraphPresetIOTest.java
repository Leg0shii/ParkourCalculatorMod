package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetFile;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetIO;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveInfo;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GraphPresetIOTest {

    private static SolverGraph rt(SolverGraph g) {
        String json = GraphPresetIO.toJson(GraphPresetIO.fromGraph(g));
        Result<GraphPresetFile> parsed = GraphPresetIO.parse(json);
        assertTrue(parsed.error, parsed.ok);
        Result<SolverGraph> materialized = GraphPresetIO.materialize(parsed.value);
        assertTrue(materialized.error, materialized.ok);
        return materialized.value;
    }

    private static GraphPresetFile.Node node(GraphPresetFile f, String id) {
        for (GraphPresetFile.Node n : f.nodes) {
            if (id.equals(n.id)) return n;
        }
        throw new IllegalStateException("no node " + id);
    }

    @Test
    public void builtinFastRoundTripsByteForByte() {
        String before = GraphPresetIO.toJson(GraphPresetIO.fromGraph(BuiltinGraphs.fast()));
        String after = GraphPresetIO.toJson(GraphPresetIO.fromGraph(rt(BuiltinGraphs.fast())));
        assertEquals(before, after);
    }

    @Test
    public void builtinOptimizeRoundTripsByteForByte() {
        String before = GraphPresetIO.toJson(GraphPresetIO.fromGraph(BuiltinGraphs.optimize(45)));
        String after = GraphPresetIO.toJson(GraphPresetIO.fromGraph(rt(BuiltinGraphs.optimize(45))));
        assertEquals(before, after);
    }

    @Test
    public void nodePositionsSurviveTheRoundTrip() {
        SolverGraph g = BuiltinGraphs.fast();
        g.node("entry").x = 40f;
        g.node("entry").y = 12.5f;
        SolverGraph back = rt(g);
        assertEquals(40f, back.node("entry").x, 0f);
        assertEquals(12.5f, back.node("entry").y, 0f);
    }

    @Test
    public void versionMismatchIsRejected() {
        GraphPresetFile f = GraphPresetIO.fromGraph(BuiltinGraphs.fast());
        f.version = 2;
        Result<GraphPresetFile> parsed = GraphPresetIO.parse(GraphPresetIO.toJson(f));
        assertFalse(parsed.ok);
        assertTrue(parsed.error, parsed.error.contains("version: 2"));
    }

    @Test
    public void invalidJsonIsRejected() {
        Result<GraphPresetFile> parsed = GraphPresetIO.parse("{ not json");
        assertFalse(parsed.ok);
    }

    @Test
    public void unknownNodeTypeNamesTheNode() {
        GraphPresetFile f = GraphPresetIO.fromGraph(BuiltinGraphs.fast());
        node(f, "freeImprove").type = "bogus";
        Result<SolverGraph> r = GraphPresetIO.materialize(f);
        assertFalse(r.ok);
        assertTrue(r.error, r.error.contains("freeImprove"));
        assertTrue(r.error, r.error.contains("bogus"));
    }

    @Test
    public void unknownBranchNamesTheEdgeSource() {
        GraphPresetFile f = GraphPresetIO.fromGraph(BuiltinGraphs.fast());
        f.edges.get(0).branch = "NOPE";
        Result<SolverGraph> r = GraphPresetIO.materialize(f);
        assertFalse(r.ok);
        assertTrue(r.error, r.error.contains("NOPE"));
        assertTrue(r.error, r.error.contains(f.edges.get(0).from));
    }

    @Test
    public void unknownParamNamesTheNode() {
        GraphPresetFile f = GraphPresetIO.fromGraph(BuiltinGraphs.fast());
        GraphPresetFile.Param p = new GraphPresetFile.Param();
        p.key = "bogusKnob";
        p.num = 1.0;
        node(f, "freeImprove").params.add(p);
        Result<SolverGraph> r = GraphPresetIO.materialize(f);
        assertFalse(r.ok);
        assertTrue(r.error, r.error.contains("bogusKnob"));
        assertTrue(r.error, r.error.contains("freeImprove"));
    }

    @Test
    public void duplicateNodeIdsAreRejected() {
        GraphPresetFile f = GraphPresetIO.fromGraph(BuiltinGraphs.fast());
        f.nodes.add(node(f, "freeImprove"));
        Result<SolverGraph> r = GraphPresetIO.materialize(f);
        assertFalse(r.ok);
        assertTrue(r.error, r.error.contains("freeImprove"));
    }

    @Test
    public void outOfRangeParamsClampToTheSpec() {
        GraphPresetFile f = GraphPresetIO.fromGraph(BuiltinGraphs.fast());
        for (GraphPresetFile.Param p : node(f, "rescueBnb").params) {
            if ("budgetSec".equals(p.key)) p.num = 99999.0;
            if ("minBudgetMs".equals(p.key)) p.num = -5.0;
        }
        Result<SolverGraph> r = GraphPresetIO.materialize(f);
        assertTrue(r.error, r.ok);
        assertEquals(600, r.value.node("rescueBnb").params.getInt("budgetSec"));
        assertEquals(0, r.value.node("rescueBnb").params.getInt("minBudgetMs"));
    }

    @Test
    public void invalidEnumValueFallsBackToTheDefault() {
        GraphPresetFile f = GraphPresetIO.fromGraph(BuiltinGraphs.fast());
        for (GraphPresetFile.Param p : node(f, "rescueBnb").params) {
            if ("mode".equals(p.key)) p.str = "NOT_A_MODE";
        }
        Result<SolverGraph> r = GraphPresetIO.materialize(f);
        assertTrue(r.error, r.ok);
        assertEquals("FIRST_FEASIBLE", r.value.node("rescueBnb").params.getString("mode"));
    }

    @Test
    public void graphWithoutEmitIsRejectedByTheValidator() {
        GraphPresetFile f = GraphPresetIO.fromGraph(BuiltinGraphs.fast());
        for (Iterator<GraphPresetFile.Node> it = f.nodes.iterator(); it.hasNext(); ) {
            if ("emit".equals(it.next().id)) it.remove();
        }
        for (Iterator<GraphPresetFile.Edge> it = f.edges.iterator(); it.hasNext(); ) {
            if ("emit".equals(it.next().to)) it.remove();
        }
        Result<SolverGraph> r = GraphPresetIO.materialize(f);
        assertFalse(r.ok);
        assertTrue(r.error, r.error.contains("Invalid graph"));
    }

    @Test
    public void storeRoundTripThroughTheGraphInfoParser() throws Exception {
        Path dir = Files.createTempDirectory("pkc-graphs");
        FileSystemSaveStore store = new FileSystemSaveStore(dir, "test", "1.8.9", null, GraphPresetIO.infoParser());
        GraphPresetFile f = GraphPresetIO.fromGraph(BuiltinGraphs.fast());
        f.name = "my-fast";
        store.write("my-fast", GraphPresetIO.toJson(f));

        List<SaveInfo> infos = store.list();
        assertEquals(1, infos.size());
        assertEquals("my-fast", infos.get(0).name);

        Result<SolverGraph> g = GraphPresetIO.loadGraph(store, "my-fast");
        assertTrue(g.error, g.ok);
        assertEquals("my-fast", g.value.name);
        assertFalse(g.value.builtin);
        assertEquals(BuiltinGraphs.fast().nodes.size(), g.value.nodes.size());
        assertEquals(BuiltinGraphs.fast().edges.size(), g.value.edges.size());
    }

    @Test
    public void loadGraphOnAMissingPresetFails() throws Exception {
        Path dir = Files.createTempDirectory("pkc-graphs-missing");
        FileSystemSaveStore store = new FileSystemSaveStore(dir, "test", "1.8.9", null, GraphPresetIO.infoParser());
        Result<SolverGraph> g = GraphPresetIO.loadGraph(store, "nope");
        assertFalse(g.ok);
    }
}
