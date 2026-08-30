package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphFactory;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetFile;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetIO;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BuiltinPresetSelectionTest {

    private static String json(SolverGraph g) {
        return GraphPresetIO.toJson(GraphPresetIO.fromGraph(g));
    }

    private static String shape(SolverGraph g) {
        GraphPresetFile f = GraphPresetIO.fromGraph(g);
        f.name = "";
        return GraphPresetIO.toJson(f);
    }

    @Test
    public void freshCustomStopsOnFeasibleByDefault() {
        assertTrue("fresh Custom must match Fast's stop-on-feasible", new AngleSolverState().isStopOnFeasible());
    }

    @Test
    public void freshCustomBuildsTheOptimizeGraphShape() {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        assertEquals(shape(BuiltinGraphs.optimize(s.getOptimizeSeconds())), shape(GraphFactory.forState(s)));
    }

    @Test
    public void selectingTheFastBuiltinBuildsFast() {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        s.setGraphPresetName(BuiltinGraphs.FAST_PRESET);
        assertEquals(json(BuiltinGraphs.fast()), json(GraphFactory.forState(s)));
    }

    @Test
    public void selectingTheOptimizeBuiltinBuildsOptimizeWithTheStateSeconds() {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        s.setGraphPresetName(BuiltinGraphs.OPTIMIZE_PRESET);
        s.setOptimizeSeconds(45);
        assertEquals(json(BuiltinGraphs.optimize(45)), json(GraphFactory.forState(s)));
        s.setOptimizeSeconds(120);
        assertEquals(json(BuiltinGraphs.optimize(120)), json(GraphFactory.forState(s)));
    }

    @Test
    public void unknownCustomPresetFallsBackToOptimize() {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        s.setGraphPresetName("longer");
        s.setOptimizeSeconds(60);
        assertEquals(json(BuiltinGraphs.optimize(60)), json(GraphFactory.forState(s)));
    }
}
