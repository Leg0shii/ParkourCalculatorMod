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

public class NixSeamMap {

    private ExactJumpModel model;
    private JumpSpec fullSpec;
    private JumpPhysicsInputs full;
    private double[] dgfFull;
    private final SolveCore.Budget budget = new SolveCore.Budget(256, 100000, 64, BucketAscentPolish.THOROUGH);

    @Test
    public void map() throws Exception {
        String path = System.getenv("PKC_SEAM_FILE");
        org.junit.Assume.assumeTrue("set PKC_SEAM_FILE", path != null && !path.isEmpty());
        int seam = Integer.parseInt(System.getenv().getOrDefault("PKC_SEAM_TICK", "42"));
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
        dgfFull = full.toGameFacings(Angles.wrapAll(dyaw));
        ForwardPath dp = model.forward(full, dgfFull);

        double px = dp.posX[seam], pz = dp.posZ[seam], py = dp.posY[seam];
        double vx = dp.velX[seam], vz = dp.velZ[seam];
        float seedYaw = (float) dgfFull[seam - 1];
        System.out.printf("=== NixSeamMap %s: how far can the t%d seam drift while [%d,%d) still closes byte-exact? ===%n",
                new File(path).getName(), seam, seam, n);
        System.out.printf("proven seam pos=(%.5f,%.5f) vel=(%.5f,%.5f)%n", px, pz, vx, vz);

        double base = closeViol(seam, n, px, py, pz, vx, vz, seedYaw);
        System.out.printf("baseline (proven seam): SolveCore [%d,%d) viol=%.3e  %s%n%n",
                seam, n, base, base <= 0.0 ? "closes" : "does NOT close");

        double[] deltas = {1.0e-5, 3.0e-5, 1.0e-4, 3.0e-4, 1.0e-3, 3.0e-3};
        sweep("pos Z", seam, n, deltas, d -> closeViol(seam, n, px, py, pz + d, vx, vz, seedYaw));
        sweep("pos X", seam, n, deltas, d -> closeViol(seam, n, px + d, py, pz, vx, vz, seedYaw));
        sweep("vel Z", seam, n, deltas, d -> closeViol(seam, n, px, py, pz, vx, vz + d, seedYaw));
        sweep("vel X", seam, n, deltas, d -> closeViol(seam, n, px, py, pz, vx + d, vz, seedYaw));
    }

    private interface DFun { double at(double d); }

    private void sweep(String name, int seam, int n, double[] deltas, DFun f) {
        System.out.printf("-- drift %s (F=closes byte-exact, . = viol>0) --%n", name);
        StringBuilder line = new StringBuilder("   ");
        for (double d : deltas) {
            boolean plus = f.at(d) <= 0.0;
            boolean minus = f.at(-d) <= 0.0;
            line.append(String.format(" +/-%.0e:%s%s", d, plus ? "F" : ".", minus ? "F" : "."));
        }
        System.out.println(line);
    }

    private double closeViol(int a, int n, double px, double py, double pz, double vx, double vz, float seedYaw) {
        JumpPhysicsInputs win = sliceScenario(full, a, n, new Vec3dCore(px, py, pz), new Vec3dCore(vx, 0.0, vz), seedYaw);
        List<JumpConstraint> cons = sliceConstraints(fullSpec, a, n);
        JumpSpec spec = new JumpSpec(win, cons, new Objective(fullSpec.objective.axis, fullSpec.objective.sense, n - a));
        double[] y = SolveCore.optimize(model, spec, budget, 20.0, 0.0, new AtomicBoolean(false), null);
        if (y == null) return Double.POSITIVE_INFINITY;
        double[] gf = win.toGameFacings(Angles.wrapAll(y));
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, model.forward(win, gf));
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
