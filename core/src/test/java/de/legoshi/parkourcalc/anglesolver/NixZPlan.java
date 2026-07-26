package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public class NixZPlan {

    private ExactJumpModel model;
    private JumpPhysicsInputs full;

    private double[] stepZ(double z, double vz, int absTick, double facing) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(1);
        sc.startPos = new Vec3dCore(11.0, full.startPos.y, z);
        sc.initialVelocity = new Vec3dCore(0.0, 0.0, vz);
        sc.startYaw = (float) facing;
        sc.incomingSprint = absTick == 0 ? full.incomingSprint : full.sprintAt(absTick - 1);
        sc.incomingAmp = 0;
        sc.strafeSign = full.strafeSign;
        sc.jumpPerTick = new boolean[]{full.jumpAt(absTick)};
        sc.slipPerTick = new double[]{full.slipAt(absTick)};
        sc.strafePerTick = new boolean[]{full.strafeAt(absTick)};
        sc.sprintPerTick = new boolean[]{full.sprintAt(absTick)};
        sc.forwardInputPerTick = new float[]{full.forwardAt(absTick)};
        sc.strafeInputPerTick = new float[]{full.strafeInputAt(absTick)};
        sc.speedAmplifier = new int[]{0};
        sc.yawLockedPerTick = new boolean[]{false};
        ForwardPath p = model.forward(sc, new double[]{facing});
        return new double[]{p.posZ[1], p.velZ[1]};
    }

    @Test
    public void plan() throws Exception {
        String path = System.getenv("PKC_ZP_FILE");
        org.junit.Assume.assumeTrue("set PKC_ZP_FILE", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setStartTick(0);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        full = spec.asScenario();
        System.out.println("=== NixZPlan: 1-D Z bang-bang over hop t12 / j2 t25 / j3 t37 / run t49 / j4 t50 ===");

        double zBoxLo = 1.512499988079071, zBoxHi = 7.3;
        double bestVz = Double.NEGATIVE_INFINITY;
        String bestDesc = "";
        double[] bestOut = null;

        for (double preVz = -0.30; preVz <= 0.25 + 1e-9; preVz += 0.01) {
            for (int hopFace = 0; hopFace <= 1; hopFace++) {
                double hopYaw = hopFace == 0 ? 0.0 : 180.0;
                for (int sw = 13; sw <= 24; sw++) {
                    for (double trimYaw = 0.0; trimYaw <= 180.0 + 1e-9; trimYaw += 7.5) {
                        double z = 2.973, vz = preVz;
                        boolean ok = true;
                        double zMin = z, zMax = z;
                        double zAt24 = 0, vzAt24 = 0;
                        for (int t = 12; t < 25 && ok; t++) {
                            double yaw;
                            if (t == 12) yaw = hopYaw;
                            else if (t < sw) yaw = 180.0;
                            else if (t == sw) yaw = trimYaw;
                            else yaw = 0.0;
                            double[] r = stepZ(z, vz, t, yaw);
                            z = r[0];
                            vz = r[1];
                            zMin = Math.min(zMin, z);
                            zMax = Math.max(zMax, z);
                            if (t == 24) {
                                zAt24 = z;
                                vzAt24 = vz;
                            }
                        }
                        if (zMin < 1.2 || zMax > zBoxHi) continue;
                        double z24 = z, v24 = vz;
                        double zz = z24, vv = v24;
                        double zAt36 = 0, zAt37 = 0, zAt48 = 0, zAt49 = 0;
                        for (int t = 25; t < 50; t++) {
                            double[] r = stepZ(zz, vv, t, 0.0);
                            zz = r[0];
                            vv = r[1];
                            if (t == 36) zAt36 = zz;
                            if (t == 37) zAt37 = zz;
                            if (t == 48) zAt48 = zz;
                            if (t == 49) zAt49 = zz;
                        }
                        if (zAt36 < zBoxLo || zAt36 > zBoxHi) continue;
                        if (zAt48 < zBoxLo || zAt48 > zBoxHi) continue;
                        if (zAt49 < zBoxLo || zAt49 > zBoxHi) continue;
                        if (vv > bestVz) {
                            bestVz = vv;
                            bestOut = new double[]{zz, vv, z24, v24, zMin};
                            bestDesc = String.format(Locale.ROOT,
                                    "preVz=%+.2f hopYaw=%.0f switch=%d trim=%.1f | land24 z=%.4f vz=%+.4f | t50 z=%.4f vz=%+.4f | zMinHop=%.4f",
                                    preVz, hopYaw, sw, trimYaw, z24, v24, zz, vv, zMin);
                        }
                    }
                }
            }
        }
        System.out.println("BEST vz@50 = " + String.format(Locale.ROOT, "%.6f", bestVz));
        System.out.println(bestDesc);
        if (bestOut != null) {
            System.out.printf(Locale.ROOT, "planner entry for arc map: pz@50=%.4f vz@50=%.4f%n", bestOut[0], bestOut[1]);
        }
    }
}
