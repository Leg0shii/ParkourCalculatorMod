package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.NoTurnCapture;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnCertifier;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnFinder;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class NoTurnDriversTest {

    private static final String J1150 = "hpk_precise/j1150-noturn-inner";
    private static final String J154 = "hpk_precise/j154-noturn-ja-inner";
    private static final long GRAPH_BUDGET_NANOS = 12_000_000_000L;
    private static final long SEARCH_BUDGET_NANOS = 4_000_000_000L;

    private static boolean interior(NoTurnProblem p, NoTurnResult r) {
        if (r == null || p.freeBox == null) return false;
        return r.startX > p.freeBox.pxLo + 1e-6 && r.startX < p.freeBox.pxHi - 1e-6
                && r.startZ > p.freeBox.pzLo + 1e-6 && r.startZ < p.freeBox.pzHi - 1e-6;
    }

    @Test
    public void certifierLandsAByteExactNoTurnOnJ1150() throws Exception {
        NoTurnProblem p = NoTurnCapture.problem(J1150);
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
    public void finderReturnsAByteExactNoTurnOnJ1150() throws Exception {
        NoTurnProblem p = NoTurnCapture.problem(J1150);
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

    @Test
    public void structurePoolCracksJ1150ColdAtMinEdges() throws Exception {
        NoTurnProblem p = NoTurnCapture.problem(J1150);

        StructurePoolDriver.Config cfg = new StructurePoolDriver.Config();
        StructurePoolDriver drv = new StructurePoolDriver(p.model, cfg, new AtomicBoolean(false),
                (stage, frac) -> { });

        long t0 = System.nanoTime();
        NoTurnResult best = drv.run(p, BuiltinGraphs.optimize(8));
        double wallSec = (System.nanoTime() - t0) / 1e9;

        boolean interior = interior(p, best);
        System.out.println("j1150 StructurePoolDriver cold result:");
        System.out.println("  found=" + (best != null)
                + " scored=" + drv.scoredCount() + " byteScreened=" + drv.byteScreenedCount()
                + " pool=" + drv.pool().size());
        if (best != null) {
            System.out.println("  edges=" + best.edges + " keys=" + NoTurnKeys.describe(best.combos)
                    + " sprint@" + best.sprintEngage + " ja=" + best.ja);
            System.out.println("  objective=" + best.objective + " (ref -2805.2990460856336)"
                    + " violation=" + best.violation);
            System.out.println("  freeStart px=" + best.startX + " pz=" + best.startZ
                    + " interior=" + interior + " box px[" + p.freeBox.pxLo + "," + p.freeBox.pxHi
                    + "] pz[" + p.freeBox.pzLo + "," + p.freeBox.pzHi + "]");
        }
        System.out.println("  wallClock=" + wallSec + " s");

        assertNotNull("cold structure-pool finds a byte-exact no-turn", best);
        assertTrue("byte-exact clean (ExactJumpModel certify at FEAS_TOL=0)", best.violation <= 0.0);
        assertTrue("min-edges within budget (target 3)", best.edges <= 4);
        assertTrue("objective near the pure-no-turn optimum (X MAX)",
                best.objective >= -2805.30 && best.objective <= -2805.298);
        assertTrue("free start interior to the box (joint free-start search decisive)", interior);
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

    private static void comparePlayable(String capture, boolean ja) throws Exception {
        NoTurnProblem p = NoTurnCapture.problem(capture);
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
    public void playableCertifyStaysByteExactOnJ1150PureNoTurn() throws Exception {
        comparePlayable(J1150, false);
    }

    @Test
    public void playableCertifyStaysByteExactOnJ154JumpAngle() throws Exception {
        comparePlayable(J154, true);
    }
}
