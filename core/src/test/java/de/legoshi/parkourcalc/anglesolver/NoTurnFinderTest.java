package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnCertifier;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnFinder;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class NoTurnFinderTest {

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

    @Test
    public void machineryCertifiesAByteExactNoTurn() throws Exception {
        NoTurnProblem p = load("hpk_precise/j1150-noturn-inner");
        assertEquals(38, p.setupEnd);
        assertTrue("free-start box derived", p.freeBox != null);
        assertTrue("landing walls present", p.walls.size() >= 6);

        NoTurnCertifier cert = new NoTurnCertifier(p.model);
        SolverGraph graph = BuiltinGraphs.optimize(10);
        NoTurnCertifier.Result r = cert.certify(p.baseSpecWithDf(false), graph, 12_000_000_000L, new AtomicBoolean(false));
        System.out.println("j1150 no-turn machinery: feasible=" + r.feasible + " obj=" + r.objective
                + " viol=" + r.violation + " (ref -2805.2990460856336)");
        assertTrue("byte-exact feasible", r.feasible);
        assertTrue("byte-exact clean", r.violation <= 0.0);
        assertTrue("objective near the pure-no-turn optimum",
                r.objective <= -2805.29 && r.objective >= -2805.31);
    }

    @Test
    public void finderReturnsAByteExactNoTurn() throws Exception {
        NoTurnProblem p = load("hpk_precise/j1150-noturn-inner");
        NoTurnFinder.Config cfg = new NoTurnFinder.Config();
        cfg.beamWidth = 600;
        cfg.maxEdges = 3;
        cfg.perLevelCertify = 1;
        cfg.certifyBudgetNanos = 5_000_000_000L;
        cfg.totalCertifyBudgetNanos = 20_000_000_000L;
        NoTurnFinder finder = new NoTurnFinder(p.model, cfg, new AtomicBoolean(false),
                (stage, frac) -> { });
        NoTurnResult best = finder.run(p, BuiltinGraphs.optimize(5));
        System.out.println("j1150 finder: "
                + (best == null ? "no byte-exact no-turn found (cold + warm seed)" : best.describe()
                        + " obj=" + best.objective + " viol=" + best.violation));
        assertTrue("finder returns a byte-exact no-turn (cold beam or warm seed of the current inputs)",
                best != null && best.violation <= 0.0);
    }
}
