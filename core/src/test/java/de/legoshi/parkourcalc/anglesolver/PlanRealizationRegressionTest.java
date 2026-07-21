package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Rebuilds the engine's plan path (spec + toGameFacings, exactly as runJob and Apply's deviation
 * check use it) for a debug-enabled 26.2 capture whose rows were written by Apply, and requires the
 * recorded resim to sit on it byte-exact per clean tick. Pinned the 26.x square-movement input
 * rewrite (KeyboardInput float normalize + modifyInputSpeedForSquareMovement): force-45 diagonals
 * run at the square-stretched float input, not the pre-26 double-normalized 0.98 pair.
 */
public class PlanRealizationRegressionTest {

    @Test
    public void modern262CaptureRealizesThePlanPath() {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("deserthard-planrealization"));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        List<SaveFile.DebugTick> d = file.debug;
        int startTick = file.angleSolver.startTick;

        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        SaveFile.Start seed = file.angleSolver.seed;
        BoxController boxes = new BoxController();
        for (int i = 0; i < file.rows.size(); i++) {
            Vec3dCore pos = i == startTick ? new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]) : Vec3dCore.ZERO;
            Vec3dCore vel = i == startTick ? new Vec3dCore(seed.vel[0], seed.vel[1], seed.vel[2]) : Vec3dCore.ZERO;
            float yaw = i == startTick ? seed.yaw : 0f;
            boxes.add(new TickState(pos, false, false, false, yaw,
                    Collections.<Vec3dCore>emptyList(), vel, false, Double.NaN));
        }
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, model);
        JumpPhysicsInputs sc = engine.debugBuildSpec().asScenario();
        List<SaveFile.Yaw> resultYaws = file.angleSolver.result.yaws;
        double[] yaws = new double[resultYaws.size()];
        for (int k = 0; k < yaws.length; k++) yaws[k] = resultYaws.get(k).yaw;
        ForwardPath plan = model.forward(sc, sc.toGameFacings(yaws));

        List<String> out = new ArrayList<>();
        int bad = 0;
        int compared = 0;
        for (int k = 1; k <= yaws.length; k++) {
            SaveFile.DebugTick rec = d.get(startTick + k);
            SaveFile.DebugTick prev = d.get(startTick + k - 1);
            if (rec.wallCollision || rec.softCollision || prev.wallCollision || prev.softCollision) continue;
            compared++;
            double ddx = (rec.pos[0] - prev.pos[0]) - (plan.posX[k] - plan.posX[k - 1]);
            double ddz = (rec.pos[2] - prev.pos[2]) - (plan.posZ[k] - plan.posZ[k - 1]);
            if (Math.abs(ddx) > 1.0e-9 || Math.abs(ddz) > 1.0e-9) {
                bad++;
                if (bad <= 10) {
                    out.add(String.format("k=%d abs=%d ddx=%.3e ddz=%.3e recYaw=%.6f",
                            k, startTick + k, ddx, ddz, rec.yaw));
                }
            }
        }
        assertTrue("plan-vs-capture per-tick divergences: " + bad + " of " + compared + "\n"
                + String.join("\n", out), bad == 0);
        assertTrue("too few ticks compared: " + compared, compared >= 100);
    }
}
