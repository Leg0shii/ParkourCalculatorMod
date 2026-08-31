package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class NoTurnStructurePoolTest {

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
    public void findsColdByteExactMinEdgesNoTurn() throws Exception {
        NoTurnProblem p = load("hpk_precise/j1150-noturn-inner");

        StructurePoolDriver.Config cfg = new StructurePoolDriver.Config();
        StructurePoolDriver drv = new StructurePoolDriver(p.model, cfg, new AtomicBoolean(false),
                (stage, frac) -> { });

        long t0 = System.nanoTime();
        NoTurnResult best = drv.run(p, BuiltinGraphs.optimize(8));
        double wallSec = (System.nanoTime() - t0) / 1e9;

        boolean interior = false;
        if (best != null && p.freeBox != null) {
            interior = best.startX > p.freeBox.pxLo + 1e-6 && best.startX < p.freeBox.pxHi - 1e-6
                    && best.startZ > p.freeBox.pzLo + 1e-6 && best.startZ < p.freeBox.pzHi - 1e-6;
        }
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
}
