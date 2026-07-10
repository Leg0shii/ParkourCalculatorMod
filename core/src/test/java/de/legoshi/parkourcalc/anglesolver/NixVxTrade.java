package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class NixVxTrade {

    private ExactJumpModel model;
    private JumpSpec fullSpec;
    private JumpPhysicsInputs full;

    @Test
    public void trade() throws Exception {
        String path = System.getenv("PKC_TRADE_FILE");
        org.junit.Assume.assumeTrue("set PKC_TRADE_FILE", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        fullSpec = engine.debugBuildSpec();
        full = fullSpec.asScenario();
        int n = full.numTicks;
        double[] dyaw = new double[n];
        for (int k = 0; k < n; k++) dyaw[k] = file.debug.get(k + 1).yaw;
        ForwardPath dp = model.forward(full, full.toGameFacings(Angles.wrapAll(dyaw)));
        int s = 30;
        double px = dp.posX[s], pz = dp.posZ[s], py = dp.posY[s];
        double vx0 = dp.velX[s], vz0 = dp.velZ[s];
        float seedYaw = (float) full.toGameFacings(Angles.wrapAll(dyaw))[s - 1];
        SolveCore.Budget budget = new SolveCore.Budget(256, 100000, 64, BucketAscentPolish.THOROUGH);

        System.out.printf("=== NixVxTrade %s: does the tail [30,54) close at LOWER vz if the seam carries vx (idea #2)? ===%n",
                new File(path).getName());
        System.out.printf("proven t30 seam vel=(%.5f,%.5f); tail stuck at vz=proven, needs +0.005 without vx%n", vx0, vz0);
        System.out.println("  seam vx     vz       [30,54) viol   Z@46 rise-proxy   result");

        double[] vxs = {vx0, vx0 - 0.03, vx0 - 0.06, vx0 - 0.10, vx0 - 0.15};
        double[] vzs = {0.207, 0.200, 0.195, 0.190};
        for (double vx : vxs) {
            for (double vz : vzs) {
                JumpSpec tail = slice(s, n, new Vec3dCore(px, py, pz), new Vec3dCore(vx, 0.0, vz), seedYaw);
                double[] y = SolveCore.optimize(model, tail, budget, 20.0, 0.0, new AtomicBoolean(false), null);
                double v = y == null ? Double.NaN : viol(tail, y);
                double z46 = Double.NaN;
                if (y != null) {
                    double[] gf = tail.asScenario().toGameFacings(Angles.wrapAll(y));
                    z46 = model.forward(tail.asScenario(), gf).posZ[46 - s];
                }
                System.out.printf("  %+.4f   %.4f   %.3e     %.4f          %s%n",
                        vx, vz, v, z46, (v <= 0.0 ? "*** LANDS byte-exact ***" : "no"));
            }
        }
    }

    private JumpSpec slice(int a, int c, Vec3dCore pos, Vec3dCore vel, float yaw) {
        int len = c - a;
        JumpPhysicsInputs p = new JumpPhysicsInputs(len);
        p.startPos = pos; p.initialVelocity = vel; p.startYaw = yaw; p.strafeSign = full.strafeSign;
        p.jumpPerTick = sliceBool(full.jumpPerTick, a, len);
        p.strafePerTick = sliceBool(full.strafePerTick, a, len);
        p.yawLockedPerTick = sliceBool(full.yawLockedPerTick, a, len);
        p.speedAmplifier = sliceInt(full.speedAmplifier, a, len);
        p.slipPerTick = sliceDouble(full.slipPerTick, a, len);
        p.sprintPerTick = sliceBool(full.sprintPerTick, a, len);
        p.forwardInputPerTick = sliceFloat(full.forwardInputPerTick, a, len, 0.98F);
        p.strafeInputPerTick = sliceFloat(full.strafeInputPerTick, a, len, 0.0F);
        List<JumpConstraint> cons = new ArrayList<>();
        for (JumpConstraint jc : fullSpec.constraints) {
            boolean in1 = jc.t1 >= a && jc.t1 <= c;
            boolean in2 = jc.t2 == null || (jc.t2 >= a && jc.t2 <= c);
            if (in1 && in2) cons.add(new JumpConstraint(jc.mode, jc.t1 - a, jc.t2 == null ? null : (jc.t2 - a), jc.op, jc.cmp, jc.rhs, jc.name));
        }
        return new JumpSpec(p, cons, new Objective(fullSpec.objective.axis, fullSpec.objective.sense, c - a));
    }

    private double viol(JumpSpec spec, double[] yawsAbs) {
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, model.forward(sc, gf));
    }

    private static boolean[] sliceBool(boolean[] x, int f, int len) { if (x == null) return null; boolean[] o = new boolean[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length && x[f + i]; return o; }
    private static int[] sliceInt(int[] x, int f, int len) { if (x == null) return null; int[] o = new int[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : 0; return o; }
    private static double[] sliceDouble(double[] x, int f, int len) { if (x == null) return null; double[] o = new double[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : Double.NaN; return o; }
    private static float[] sliceFloat(float[] x, int f, int len, float d) { if (x == null) return null; float[] o = new float[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : d; return o; }
}
