package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.VerySlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.noturn.BendersMaster;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(VerySlowSolverTests.class)
public class NoTurnBendersTest {

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

    private boolean interior(NoTurnProblem p, NoTurnResult r) {
        if (r == null || p.freeBox == null) return false;
        return r.startX > p.freeBox.pxLo + 1e-6 && r.startX < p.freeBox.pxHi - 1e-6
                && r.startZ > p.freeBox.pzLo + 1e-6 && r.startZ < p.freeBox.pzHi - 1e-6;
    }

    @Test
    public void j1150ColdCracksThroughMaster() throws Exception {
        NoTurnProblem p = load("hpk_precise/j1150-noturn-inner");

        BendersMaster.Config cfg = new BendersMaster.Config();
        cfg.alphabet = new int[]{NoTurnKeys.SA, NoTurnKeys.WD, NoTurnKeys.W, NoTurnKeys.NONE,
                NoTurnKeys.WA, NoTurnKeys.SD, NoTurnKeys.S};
        cfg.minDwell = 6;
        cfg.maxEdges = 2;
        cfg.ja = false;
        cfg.mode = BendersMaster.SlaveMode.DELTA0_ONLY;
        cfg.screenOrder = true;
        cfg.screenSkip = true;
        cfg.screenKeep = 0.22;
        cfg.useCuts = false;
        cfg.delta0OptimizeSec = 8;
        cfg.maxCertifies = 24;
        cfg.refineExtraAfterIncumbent = 3;
        cfg.deadlineNanos = 300_000_000_000L;

        BendersMaster m = new BendersMaster(p.model, cfg, new AtomicBoolean(false), (s, f) -> { });
        long t0 = System.nanoTime();
        NoTurnResult best = m.solve(p, BuiltinGraphs.optimize(8));
        double wallSec = (System.nanoTime() - t0) / 1e9;

        System.out.println("=== j1150 BendersMaster cold ===");
        System.out.println(m.trace().log);
        System.out.println("  masterIterations=" + m.trace().masterIterations
                + " certifies=" + m.trace().certifies + " noGoodCuts=" + m.trace().noGoodCuts
                + " structures=" + m.trace().totalStructures + " wallSec=" + wallSec);
        if (best != null) {
            System.out.println("  edges=" + best.edges + " keys=" + NoTurnKeys.describe(best.combos)
                    + " obj=" + best.objective + " viol=" + best.violation
                    + " freeStart px=" + best.startX + " pz=" + best.startZ + " interior=" + interior(p, best));
        }

        assertNotNull("benders master cracks j1150 cold", best);
        assertTrue("byte-exact clean (FEAS_TOL=0)", best.violation <= 0.0);
        assertTrue("min-edges within budget (target 3)", best.edges <= 3);
        assertTrue("objective near pure-no-turn optimum (X MAX)",
                best.objective >= -2805.30 && best.objective <= -2805.298);
        assertTrue("free start interior to the box", interior(p, best));
    }

    @Test
    public void j154ColdCracksThroughMaster() throws Exception {
        org.junit.Assume.assumeTrue(
                "full j154 cold discovery runs about 55 minutes; opt in with -Dpkc.j154ColdCrack=true",
                Boolean.getBoolean("pkc.j154ColdCrack"));
        NoTurnProblem p = load("hpk_precise/j154-noturn-ja-inner");

        BendersMaster.Config cfg = new BendersMaster.Config();
        cfg.alphabet = new int[]{NoTurnKeys.SD, NoTurnKeys.S, NoTurnKeys.WA, NoTurnKeys.W,
                NoTurnKeys.WD, NoTurnKeys.SA, NoTurnKeys.A, NoTurnKeys.D};
        cfg.minDwell = 6;
        cfg.maxEdges = 3;
        cfg.ja = true;
        cfg.mode = BendersMaster.SlaveMode.FAT_CONTINUATION;
        cfg.coarseEdgeSorted = false;
        cfg.screenOrder = false;
        cfg.screenSkip = false;
        cfg.useCuts = false;
        cfg.fatOptimizeSec = 4;
        cfg.fatCertifyNanos = 6_000_000_000L;
        cfg.continuationLead = 25;
        cfg.continuationCap = 12;
        cfg.continuationBudgetNanos = 600_000_000_000L;
        cfg.maxCertifies = 1_000_000;
        cfg.deadlineNanos = 4_500_000_000_000L;

        BendersMaster m = new BendersMaster(p.model, cfg, new AtomicBoolean(false), (s, f) -> { });
        long t0 = System.nanoTime();
        NoTurnResult best = m.solve(p, BuiltinGraphs.optimize(8));
        double wallSec = (System.nanoTime() - t0) / 1e9;

        System.out.println("=== j154 BendersMaster cold ===");
        System.out.println(m.trace().log);
        System.out.println("  masterIterations=" + m.trace().masterIterations
                + " certifies=" + m.trace().certifies + " continuations=" + m.trace().continuations
                + " fatFeasible=" + m.trace().fatFeasible
                + " noGoodCuts=" + m.trace().noGoodCuts + " structures=" + m.trace().totalStructures);
        System.out.println("  smallestEdgeReached=" + m.trace().smallestEdgeReached
                + " smallestSurvivorEdges=" + (m.trace().smallestSurvivorEdges == Integer.MAX_VALUE
                        ? "none" : m.trace().smallestSurvivorEdges)
                + " v6AncestorProposed=" + m.trace().v6AncestorProposed
                + " v6AncestorCertifiedFat=" + m.trace().v6AncestorCertifiedFat
                + " v6AncestorContinued=" + m.trace().v6AncestorContinued
                + " ancestorContinuationIndex=" + m.trace().ancestorContinuationIndex
                + " closedContinuationIndex=" + m.trace().closedContinuationIndex
                + " v6Closed=" + m.trace().v6Closed + " wallSec=" + wallSec);
        if (best != null) {
            System.out.println("  survivor edges=" + best.edges + " keys=" + NoTurnKeys.describe(best.combos)
                    + " ja=" + best.ja + " obj=" + best.objective + " viol=" + best.violation
                    + " freeStart px=" + best.startX + " pz=" + best.startZ + " interior=" + interior(p, best));
        }

        assertNotNull("benders master cracks j154 cold", best);
        assertTrue("byte-exact clean (FEAS_TOL=0)", best.violation <= 0.0);
        assertTrue("jump-angle engaged on the last setup tick", best.ja);
        assertTrue("min-edge ja family (V6 is six edges)", best.edges <= 6);
        assertTrue("objective near the byte-exact ja optimum (X MIN, ref -1599.7001161289918)",
                best.objective >= -1599.71 && best.objective <= -1599.69);
        assertTrue("free start interior to the box", interior(p, best));
    }
}
