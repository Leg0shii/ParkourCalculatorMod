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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(VerySlowSolverTests.class)
public class NoTurnWallHomotopyCrackTest {

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
    public void continuationReachesByteExactV6() throws Exception {
        NoTurnProblem p = load("hpk_precise/j154-noturn-ja-inner");

        WallHomotopyDriver.Config cfg = new WallHomotopyDriver.Config();
        cfg.ladder = new double[]{0.30, 0.10, 0.06};
        cfg.beamCap = 6;
        cfg.beamPerEdge = 2;
        cfg.repairKeepPerTick = 7;
        cfg.repairWindowRadiusMax = 1;
        cfg.rungOptimizeSec = 3;
        cfg.rungCertifyNanos = 4_500_000_000L;
        cfg.repairCertifyCap = 40;
        cfg.repairAllowPairs = false;
        cfg.speculativeCount = 8;
        cfg.speculativeCertifyCap = 16;
        cfg.totalBudgetNanos = 1_000_000_000_000L;

        WallHomotopyDriver drv = new WallHomotopyDriver(p.model, cfg, new AtomicBoolean(false),
                (stage, frac) -> { });

        List<int[]> coarse = new ArrayList<>();
        coarse.add(fam("SDx6 Sx9 WAx13 W"));

        long t0 = System.nanoTime();
        NoTurnResult best = drv.runFromSeeds(p, BuiltinGraphs.optimize(8), coarse);
        double wallSec = (System.nanoTime() - t0) / 1e9;

        boolean interior = false;
        if (best != null && p.freeBox != null) {
            interior = best.startX > p.freeBox.pxLo + 1e-6 && best.startX < p.freeBox.pxHi - 1e-6
                    && best.startZ > p.freeBox.pzLo + 1e-6 && best.startZ < p.freeBox.pzHi - 1e-6;
        }
        System.out.println("j154 wall-homotopy from coarse windup-jump seed:");
        System.out.println("  certifies=" + drv.trace().certifies
                + " rediscoveredV6=" + drv.trace().rediscoveredV6 + " wallClock=" + wallSec + " s");
        if (best != null) {
            System.out.println("  edges=" + best.edges + " keys=" + NoTurnKeys.describe(best.combos)
                    + " sprint@" + best.sprintEngage + " ja=" + best.ja
                    + " objective=" + best.objective + " violation=" + best.violation);
            System.out.println("  freeStart px=" + best.startX + " pz=" + best.startZ + " interior=" + interior);
        }

        assertNotNull("continuation closes a byte-exact ja family at delta=0", best);
        assertTrue("byte-exact clean (ExactJumpModel certify at FEAS_TOL=0)", best.violation <= 0.0);
        assertTrue("jump-angle engaged on the last setup tick", best.ja);
        assertTrue("objective near the byte-exact ja optimum (X MIN, ref -1599.7001161289918)",
                best.objective >= -1599.71 && best.objective <= -1599.69);
        assertTrue("min-edge ja family (V6 is six edges)", best.edges <= 6);
        assertTrue("free start interior to the box", interior);
    }

    private static int[] fam(String spec) {
        String[] segs = spec.trim().split("\\s+");
        int[] c = new int[29];
        int t = 0;
        for (String s : segs) {
            int mult = 1;
            String label = s;
            int x = s.indexOf('x');
            if (x >= 0) {
                label = s.substring(0, x);
                mult = Integer.parseInt(s.substring(x + 1));
            }
            int combo = keyOf(label);
            for (int i = 0; i < mult; i++) c[t++] = combo;
        }
        return c;
    }

    private static int keyOf(String k) {
        switch (k) {
            case "W": return NoTurnKeys.W;
            case "WA": return NoTurnKeys.WA;
            case "WD": return NoTurnKeys.WD;
            case "A": return NoTurnKeys.A;
            case "D": return NoTurnKeys.D;
            case "S": return NoTurnKeys.S;
            case "SA": return NoTurnKeys.SA;
            case "SD": return NoTurnKeys.SD;
            default: throw new IllegalStateException("unknown key " + k);
        }
    }
}
