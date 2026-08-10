package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.Collections;
import java.util.List;

public final class LineSpec {

    public static final float KEY_INPUT_SCALE = 0.98F;

    private LineSpec() {
    }

    public static JumpSpec build(KeyLine line, double facingDeg, double startX, double startZ) {
        ColdProblem p = line.problem;
        InputData inputs = new InputData();
        List<InputRow> rows = inputs.getRows();
        rows.clear();
        rows.addAll(line.toRows());

        BoxController boxes = buildBoxes(line, facingDeg, startX, startZ);
        AngleSolverEngine engine = new AngleSolverEngine(p.state, boxes, inputs, t -> {
        }, p.model);
        return engine.debugBuildSpec();
    }

    public static BoxController buildBoxes(KeyLine line, double facingDeg, double startX, double startZ) {
        ColdProblem p = line.problem;
        boolean[] sprint = line.sprintStates();
        BoxController boxes = new BoxController();
        for (int t = 0; t <= p.landingTick; t++) {
            if (t == p.startTick) {
                boxes.add(new TickState(new Vec3dCore(startX, 0.0, startZ), true, false, false,
                        (float) facingDeg, Collections.<Vec3dCore>emptyList(), Vec3dCore.GROUND_REST_VELOCITY,
                        false, Double.NaN, false, 0.0F, 0.0F));
            } else if (t > p.startTick && t - 1 - p.startTick < p.numTicks) {
                int seg = t - 1 - p.startTick;
                int combo = line.comboAt(seg);
                float fwd = KEY_INPUT_SCALE * KeyLine.FORWARD_SIGN[combo];
                float str = KEY_INPUT_SCALE * KeyLine.STRAFE_SIGN[combo];
                boxes.add(new TickState(Vec3dCore.ZERO, false, false, false, 0.0F,
                        Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN,
                        sprint[seg], fwd, str));
            } else {
                boxes.add(new TickState(Vec3dCore.ZERO, false, false, false, 0.0F,
                        Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN,
                        false, Float.NaN, Float.NaN));
            }
        }
        return boxes;
    }
}
