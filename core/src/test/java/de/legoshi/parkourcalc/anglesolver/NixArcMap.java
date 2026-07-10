package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.HomotopyCloser;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class NixArcMap {

    private ExactJumpModel model;
    private JumpPhysicsInputs full;
    private JumpSpec fullSpec;
    private int startTick;

    @Test
    public void map() throws Exception {
        String path = System.getenv("PKC_AM_FILE");
        org.junit.Assume.assumeTrue("set PKC_AM_FILE", path != null && !path.isEmpty());
        int arcStartAbs = Integer.parseInt(System.getenv().getOrDefault("PKC_AM_ARCSTART", "49"));
        double px = Double.parseDouble(System.getenv().getOrDefault("PKC_AM_PX", "9.7"));
        double pz = Double.parseDouble(System.getenv().getOrDefault("PKC_AM_PZ", "6.46"));
        long t0 = System.nanoTime();
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
        startTick = state.getStartTick();
        int n = full.numTicks;
        int a = arcStartAbs - startTick;
        System.out.printf(Locale.ROOT, "=== NixArcMap %s arc [%d,%d) abs, entry pos=(%.3f,%.3f) ===%n",
                new File(path).getName(), arcStartAbs, startTick + n, px, pz);

        AtomicBoolean cancel = new AtomicBoolean(false);
        SolveCore.Budget budget = new SolveCore.Budget(
                Integer.parseInt(System.getenv().getOrDefault("PKC_AM_RESTARTS", "64")), 60000, 4, BucketAscentPolish.FAST);
        double vzLo = Double.parseDouble(System.getenv().getOrDefault("PKC_AM_VZLO", "0.20"));
        double vzHi = Double.parseDouble(System.getenv().getOrDefault("PKC_AM_VZHI", "0.42"));
        double vzStep = Double.parseDouble(System.getenv().getOrDefault("PKC_AM_VZSTEP", "0.02"));
        double vxLo = Double.parseDouble(System.getenv().getOrDefault("PKC_AM_VXLO", "-0.30"));
        double vxHi = Double.parseDouble(System.getenv().getOrDefault("PKC_AM_VXHI", "0.02"));
        double vxStep = Double.parseDouble(System.getenv().getOrDefault("PKC_AM_VXSTEP", "0.04"));
        long closerS = Long.parseLong(System.getenv().getOrDefault("PKC_AM_CLOSER_S", "30"));
        double bestViol = Double.POSITIVE_INFINITY;
        for (double vz = vzLo; vz <= vzHi + 1e-9; vz += vzStep) {
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "vz=%.3f: ", vz));
            for (double vx = vxLo; vx <= vxHi + 1e-9; vx += vxStep) {
                JumpSpec arc = sliced(a, n, new Vec3dCore(px, 0, pz), new Vec3dCore(vx, 0.0, vz), 0.0f);
                double[] y = SolveCore.optimize(model, arc, budget, 25.0, 0.0, cancel, null);
                double v = y == null ? Double.NaN : Math.max(0.0, HomotopyCloser.slack(model, arc, y));
                if (!Double.isNaN(v) && v > 0.0 && v < 6.0e-3) {
                    double[] closed = HomotopyCloser.close(model, arc, y, Math.max(2.0 * v, 1.0e-3),
                            System.nanoTime() + closerS * 1_000_000_000L, cancel);
                    if (closed != null) v = 0.0;
                }
                if (v <= 0.0) {
                    System.out.printf(Locale.ROOT, "%nLANDING CELL pos=(%.3f,%.3f) vel=(%.3f,%.3f)%n", px, pz, vx, vz);
                }
                if (!Double.isNaN(v) && v < bestViol) bestViol = v;
                line.append(Double.isNaN(v) ? "  ....  " : v <= 0.0 ? "  LAND  " : String.format(Locale.ROOT, " %.1e", v));
            }
            System.out.println(line + String.format(Locale.ROOT, "  (%.0fs)", sec(t0)));
        }
        System.out.printf(Locale.ROOT, "best arc viol over grid: %.4e%n", bestViol);
    }

    private JumpSpec sliced(int a, int c, Vec3dCore pos, Vec3dCore vel, float yaw) {
        int len = c - a;
        JumpPhysicsInputs p = new JumpPhysicsInputs(len);
        p.startPos = pos;
        p.initialVelocity = vel;
        p.startYaw = yaw;
        p.strafeSign = full.strafeSign;
        p.incomingSprint = full.sprintAt(a - 1);
        p.incomingAmp = full.speedAmplifierAt(a - 1);
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
            if (in1 && in2) {
                Integer t2 = jc.t2 == null ? null : (jc.t2 - a);
                cons.add(new JumpConstraint(jc.mode, jc.t1 - a, t2, jc.op, jc.cmp, jc.rhs, jc.name));
            }
        }
        return new JumpSpec(p, cons, new Objective(fullSpec.objective.axis, fullSpec.objective.sense, c - a));
    }

    private static double sec(long t0) {
        return (System.nanoTime() - t0) / 1.0e9;
    }

    private static boolean[] sliceBool(boolean[] x, int f, int len) { if (x == null) return null; boolean[] o = new boolean[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length && x[f + i]; return o; }
    private static int[] sliceInt(int[] x, int f, int len) { if (x == null) return null; int[] o = new int[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : 0; return o; }
    private static double[] sliceDouble(double[] x, int f, int len) { if (x == null) return null; double[] o = new double[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : Double.NaN; return o; }
    private static float[] sliceFloat(float[] x, int f, int len, float d) { if (x == null) return null; float[] o = new float[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : d; return o; }
}
