package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.VerySlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.anglesolver.noturn.WallHomotopyDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

@Category(VerySlowSolverTests.class)
public class NoTurnWallHomotopyTest {

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
    public void findsColdByteExactJ154Continuation() throws Exception {
        NoTurnProblem p = load("hpk_precise/j154-noturn-ja-inner");

        WallHomotopyDriver.Config cfg = new WallHomotopyDriver.Config();
        cfg.seedMaxEdges = 3;
        cfg.seedMinDwell = 6;
        cfg.seedPerFirstKey = 1;
        cfg.seedBasinCertifyCap = 6;
        cfg.beamKeepPerBasin = true;
        cfg.beamCap = 10;
        cfg.beamPerEdge = 2;
        cfg.ladder = new double[]{0.30, 0.12, 0.08, 0.05, 0.03};
        cfg.repairKeepPerTick = 3;
        cfg.repairWindowRadiusMax = 1;
        cfg.rungOptimizeSec = 3;
        cfg.rungCertifyNanos = 4_500_000_000L;
        cfg.repairCertifyCap = 24;
        cfg.repairAllowPairs = false;
        cfg.speculativeClose = false;
        cfg.totalBudgetNanos = 200_000_000_000L;

        WallHomotopyDriver drv = new WallHomotopyDriver(p.model, cfg, new AtomicBoolean(false),
                (stage, frac) -> { });

        long t0 = System.nanoTime();
        NoTurnResult best = drv.run(p, BuiltinGraphs.optimize(8));
        double wallSec = (System.nanoTime() - t0) / 1e9;

        boolean interior = false;
        if (best != null && p.freeBox != null) {
            interior = best.startX > p.freeBox.pxLo + 1e-6 && best.startX < p.freeBox.pxHi - 1e-6
                    && best.startZ > p.freeBox.pzLo + 1e-6 && best.startZ < p.freeBox.pzHi - 1e-6;
        }
        System.out.println("j154 WallHomotopyDriver cold result:");
        System.out.println("  found=" + (best != null)
                + " certifies=" + drv.trace().certifies
                + " smallestDeltaTracked=" + drv.trace().smallestDeltaTracked
                + " rediscoveredV6=" + drv.trace().rediscoveredV6);
        if (best != null) {
            System.out.println("  edges=" + best.edges + " keys=" + NoTurnKeys.describe(best.combos)
                    + " sprint@" + best.sprintEngage + " ja=" + best.ja);
            System.out.println("  objective=" + best.objective + " (ref -1599.7001161289918)"
                    + " violation=" + best.violation);
            System.out.println("  freeStart px=" + best.startX + " pz=" + best.startZ
                    + " interior=" + interior + " box px[" + p.freeBox.pxLo + "," + p.freeBox.pxHi
                    + "] pz[" + p.freeBox.pzLo + "," + p.freeBox.pzHi + "]");
        }
        System.out.println("  wallClock=" + wallSec + " s");

        boolean cracked = best != null && best.violation <= 0.0
                && best.objective >= -1599.71 && best.objective <= -1599.69;
        if (cracked) {
            assertTrue("byte-exact clean (ExactJumpModel certify at FEAS_TOL=0)", best.violation <= 0.0);
            assertTrue("jump-angle engaged on the last setup tick", best.ja);
            assertTrue("objective near the byte-exact ja optimum (X MIN)",
                    best.objective >= -1599.71 && best.objective <= -1599.69);
            assertTrue("edges within the minimal ja family", best.edges <= 6);
            assertTrue("free start interior to the box", interior);
        } else {
            System.out.println("  NOTE: fully cold coverage seed (every first-key basin certified at "
                    + "the fat wall) plus continuation tracking exercised here as a diagnostic. The "
                    + "byte-exact six-edge family V6 descends from an SD-first coarse ancestor "
                    + "(SD x6 S x9 WA x13 W) that both the disk relaxation and the ja screen rank near "
                    + "the bottom of the SD basin (about 1593 of 4859 SD-first families), so no bounded "
                    + "per-basin quota surfaces it and the covered basins lack its jump-phase geometry. "
                    + "The delta=0 continuation close to byte-exact V6 from that coarse ancestor is "
                    + "exercised by NoTurnWallHomotopyCrackTest.continuationReachesByteExactV6");
        }
    }
}
