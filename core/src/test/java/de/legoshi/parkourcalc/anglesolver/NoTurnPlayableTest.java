package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnCertifier;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class NoTurnPlayableTest {

    private static final long GRAPH_BUDGET_NANOS = 12_000_000_000L;
    private static final long SEARCH_BUDGET_NANOS = 4_000_000_000L;

    private NoTurnProblem load(String capture) throws Exception {
        String raw = Fixtures.rawPool(capture);
        SaveFile file = SaveIO.parseSafe(raw);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        return NoTurnProblem.from(engine.debugBuildSpec(), model);
    }

    private static double maxTurn(double[] yaws) {
        double m = 0.0;
        for (int t = 1; t < yaws.length; t++) {
            double d = yaws[t] - yaws[t - 1];
            d -= 360.0 * Math.round(d / 360.0);
            m = Math.max(m, Math.abs(d));
        }
        return m;
    }

    private static String summary(String label, NoTurnCertifier.Result r, long ms, int setupEnd) {
        if (r == null || !r.feasible) return label + ": infeasible (" + ms + " ms)";
        StringBuilder deltas = new StringBuilder();
        for (int t = Math.max(1, setupEnd); t < r.yaws.length; t++) {
            double d = r.yaws[t] - r.yaws[t - 1];
            d -= 360.0 * Math.round(d / 360.0);
            deltas.append(String.format(Locale.ROOT, " %+.3f", d));
        }
        return String.format(Locale.ROOT, "%s: obj=%.7f viol=%.3g reversals=%d maxTurn=%.2f (%d ms) air deltas:%s",
                label, r.objective, r.violation, NoTurnCertifier.reversals(r.yaws), maxTurn(r.yaws), ms, deltas);
    }

    private void compare(String capture, boolean ja) throws Exception {
        NoTurnProblem p = load(capture);
        JumpSpec spec = p.baseSpecWithDf(ja);
        SolverGraph graph = BuiltinGraphs.optimize(6);
        NoTurnCertifier plain = new NoTurnCertifier(p.model, false);
        NoTurnCertifier playable = new NoTurnCertifier(p.model, true);
        AtomicBoolean cancel = new AtomicBoolean(false);

        long t0 = System.nanoTime();
        NoTurnCertifier.Result plainSearch = plain.certifySearch(spec, SEARCH_BUDGET_NANOS, cancel);
        long t1 = System.nanoTime();
        NoTurnCertifier.Result playableSearch = playable.certifySearch(spec, SEARCH_BUDGET_NANOS, cancel);
        long t2 = System.nanoTime();
        NoTurnCertifier.Result plainGraph = plain.certify(spec, graph, GRAPH_BUDGET_NANOS, cancel);
        long t3 = System.nanoTime();
        NoTurnCertifier.Result playableGraph = playable.certify(spec, graph, GRAPH_BUDGET_NANOS, cancel);
        long t4 = System.nanoTime();

        System.out.println(capture + (ja ? " (ja)" : " (pure)"));
        System.out.println("  " + summary("search plain   ", plainSearch, (t1 - t0) / 1_000_000L, p.setupEnd));
        System.out.println("  " + summary("search playable", playableSearch, (t2 - t1) / 1_000_000L, p.setupEnd));
        System.out.println("  " + summary("graph  plain   ", plainGraph, (t3 - t2) / 1_000_000L, p.setupEnd));
        System.out.println("  " + summary("graph  playable", playableGraph, (t4 - t3) / 1_000_000L, p.setupEnd));

        if (plainSearch.feasible) {
            assertTrue("playable search certify stays feasible", playableSearch.feasible && playableSearch.violation <= 0.0);
            assertTrue("playable search line has no more reversals",
                    NoTurnCertifier.reversals(playableSearch.yaws) <= NoTurnCertifier.reversals(plainSearch.yaws));
        }
        if (plainGraph.feasible) {
            assertTrue("playable graph certify stays feasible", playableGraph.feasible && playableGraph.violation <= 0.0);
            assertTrue("playable graph line has no more reversals",
                    NoTurnCertifier.reversals(playableGraph.yaws) <= NoTurnCertifier.reversals(plainGraph.yaws));
        }
    }

    @Test
    public void j1150PureNoTurn() throws Exception {
        compare("hpk_precise/j1150-noturn-inner", false);
    }

    @Test
    public void j154JumpAngle() throws Exception {
        compare("hpk_precise/j154-noturn-ja-inner", true);
    }
}
