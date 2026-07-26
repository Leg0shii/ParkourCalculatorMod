package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
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

public class NixMinVz {

    private ExactJumpModel model;
    private JumpSpec fullSpec;
    private JumpPhysicsInputs full;
    private final SolveCore.Budget budget = new SolveCore.Budget(256, 100000, 64, BucketAscentPolish.THOROUGH);

    @Test
    public void minvz() throws Exception {
        String path = System.getenv("PKC_MINVZ_FILE");
        org.junit.Assume.assumeTrue("set PKC_MINVZ_FILE", path != null && !path.isEmpty());
        int a = Integer.parseInt(System.getenv().getOrDefault("PKC_MINVZ_SEAM", "42"));
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
        double[] dgf = full.toGameFacings(Angles.wrapAll(dyaw));
        ForwardPath dp = model.forward(full, dgf);

        double px0 = dp.posX[a], pz0 = dp.posZ[a], py0 = dp.posY[a];
        double vx0 = dp.velX[a], vz0 = dp.velZ[a];
        float seedYaw = (float) dgf[a - 1];
        System.out.printf("=== NixMinVz %s: minimal landable takeoff vz for [%d,%d), POSITION-FREE ===%n",
                new File(path).getName(), a, n);
        System.out.printf("proven takeoff t%d: pos=(%.4f,%.4f) vel=(%.5f,%.5f)%n", a, px0, pz0, vx0, vz0);

        System.out.println("vz sweep (fixed pos vs position-free via recoverStart):");
        System.out.println("   vz        fixedPosViol   freePosViol   start(px,pz) that falls out");
        double minFixed = Double.NaN, minFree = Double.NaN;
        double[] offs = {0.010, 0.005, 0.000, -0.005, -0.010, -0.020, -0.030, -0.040};
        for (double off : offs) {
            double vz = vz0 + off;
            double fixedViol = fixedViol(a, n, px0, py0, pz0, vx0, vz, seedYaw);
            double[] free = freeViol(a, n, px0, py0, pz0, vx0, vz, seedYaw);
            if (fixedViol <= 0.0) minFixed = vz;
            if (free[0] <= 0.0) minFree = vz;
            System.out.printf("  %.5f   %.3e      %.3e     (%.4f,%.4f) %s%n",
                    vz, fixedViol, free[0], free[1], free[2], free[0] <= 0.0 ? "LANDS" : "");
        }
        System.out.printf("%n=> minimal landable vz: fixed-position=%.5f  position-free=%.5f  (proven=%.5f)%n",
                minFixed, minFree, vz0);
        System.out.printf("   position-free buys %.5f more vz headroom (lower minimal takeoff momentum)%n",
                Double.isNaN(minFixed) || Double.isNaN(minFree) ? Double.NaN : minFixed - minFree);
    }

    private double fixedViol(int a, int n, double px, double py, double pz, double vx, double vz, float seedYaw) {
        JumpSpec spec = slice(a, n, new Vec3dCore(px, py, pz), new Vec3dCore(vx, 0.0, vz), seedYaw, false);
        double[] y = SolveCore.optimize(model, spec, budget, 20.0, 0.0, new AtomicBoolean(false), null);
        return y == null ? Double.POSITIVE_INFINITY : viol(spec, y);
    }

    private double[] freeViol(int a, int n, double px, double py, double pz, double vx, double vz, float seedYaw) {
        double bestViol = Double.POSITIVE_INFINITY, bestPx = px, bestPz = pz;
        double refX = px, refZ = pz;
        for (int iter = 0; iter < 3; iter++) {
            JumpSpec spec = slice(a, n, new Vec3dCore(refX, py, refZ), new Vec3dCore(vx, 0.0, vz), seedYaw, true);
            double[] y = SolveCore.optimize(model, spec, budget, 20.0, 0.0, new AtomicBoolean(false), null);
            if (y == null) continue;
            double[] rs = FreeStartSolve.recoverStart(model, spec, y);
            double v; double qx, qz;
            if (rs == null) { v = viol(spec, y); qx = refX; qz = refZ; }
            else { v = FreeStartSolve.violationAt(model, spec, y, rs[0], rs[1]); qx = rs[0]; qz = rs[1]; }
            if (v < bestViol) { bestViol = v; bestPx = qx; bestPz = qz; }
            if (v <= 0.0) break;
            refX = qx; refZ = qz;
        }
        return new double[]{bestViol, bestPx, bestPz};
    }

    private JumpSpec slice(int a, int c, Vec3dCore pos, Vec3dCore vel, float yaw, boolean freeBox) {
        JumpPhysicsInputs win = sliceScenario(full, a, c, pos, vel, yaw);
        if (freeBox) {
            win.startBox = new StartBox(pos.x, pos.z, vel.x, vel.z,
                    pos.x - 20.0, pos.x + 20.0, pos.z - 20.0, pos.z + 20.0, vel.x, vel.x, vel.z, vel.z);
        }
        List<JumpConstraint> cons = sliceConstraints(fullSpec, a, c);
        return new JumpSpec(win, cons, new Objective(fullSpec.objective.axis, fullSpec.objective.sense, c - a));
    }

    private double viol(JumpSpec spec, double[] yawsAbs) {
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, model.forward(sc, gf));
    }

    private static JumpPhysicsInputs sliceScenario(JumpPhysicsInputs sc, int a, int c,
                                                   Vec3dCore pos, Vec3dCore vel, float yaw) {
        int len = c - a;
        JumpPhysicsInputs p = new JumpPhysicsInputs(len);
        p.startPos = pos;
        p.initialVelocity = vel;
        p.startYaw = yaw;
        p.strafeSign = sc.strafeSign;
        p.jumpPerTick = sliceBool(sc.jumpPerTick, a, len);
        p.strafePerTick = sliceBool(sc.strafePerTick, a, len);
        p.yawLockedPerTick = sliceBool(sc.yawLockedPerTick, a, len);
        p.speedAmplifier = sliceInt(sc.speedAmplifier, a, len);
        p.slipPerTick = sliceDouble(sc.slipPerTick, a, len);
        p.sprintPerTick = sliceBool(sc.sprintPerTick, a, len);
        p.forwardInputPerTick = sliceFloat(sc.forwardInputPerTick, a, len, 1.0F * 0.98F);
        p.strafeInputPerTick = sliceFloat(sc.strafeInputPerTick, a, len, 0.0F);
        return p;
    }

    private static List<JumpConstraint> sliceConstraints(JumpSpec fullSpec, int a, int c) {
        List<JumpConstraint> out = new ArrayList<>();
        for (JumpConstraint jc : fullSpec.constraints) {
            boolean in1 = jc.t1 >= a && jc.t1 <= c;
            boolean in2 = jc.t2 == null || (jc.t2 >= a && jc.t2 <= c);
            if (in1 && in2) {
                Integer t2 = jc.t2 == null ? null : (jc.t2 - a);
                out.add(new JumpConstraint(jc.mode, jc.t1 - a, t2, jc.op, jc.cmp, jc.rhs, jc.name));
            }
        }
        return out;
    }

    private static boolean[] sliceBool(boolean[] x, int from, int len) {
        if (x == null) return null;
        boolean[] o = new boolean[len];
        for (int i = 0; i < len; i++) o[i] = from + i < x.length && x[from + i];
        return o;
    }

    private static int[] sliceInt(int[] x, int from, int len) {
        if (x == null) return null;
        int[] o = new int[len];
        for (int i = 0; i < len; i++) o[i] = from + i < x.length ? x[from + i] : 0;
        return o;
    }

    private static double[] sliceDouble(double[] x, int from, int len) {
        if (x == null) return null;
        double[] o = new double[len];
        for (int i = 0; i < len; i++) o[i] = from + i < x.length ? x[from + i] : Double.NaN;
        return o;
    }

    private static float[] sliceFloat(float[] x, int from, int len, float dflt) {
        if (x == null) return null;
        float[] o = new float[len];
        for (int i = 0; i < len; i++) o[i] = from + i < x.length ? x[from + i] : dflt;
        return o;
    }
}
