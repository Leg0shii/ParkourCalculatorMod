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

public class NixChain {

    private ExactJumpModel model;
    private JumpSpec fullSpec;
    private JumpPhysicsInputs full;
    private double[] dgfFull;
    private final SolveCore.Budget budget = new SolveCore.Budget(256, 100000, 64, BucketAscentPolish.THOROUGH);

    @Test
    public void chain() throws Exception {
        String path = System.getenv("PKC_CHAIN_FILE");
        org.junit.Assume.assumeTrue("set PKC_CHAIN_FILE", path != null && !path.isEmpty());
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
        this.dgfFull = dgf;

        System.out.printf("=== NixChain %s (Stage A: split-tail chain from proven t30 seam) ===%n", new File(path).getName());
        int s30 = 30, s42 = 42;

        // Proven t30 seam.
        Vec3dCore p30 = new Vec3dCore(dp.posX[s30], dp.posY[s30], dp.posZ[s30]);
        Vec3dCore v30 = new Vec3dCore(dp.velX[s30], dp.velY[s30], dp.velZ[s30]);
        System.out.printf("proven t30 seam pos=(%.4f,%.4f) vel=(%.5f,%.5f)%n", p30.x, p30.z, v30.x, v30.z);

        // Step 1: near-feasible [30,54) (cold) to pick a good t42 seam.
        JumpSpec tail30 = sliced(s30, n, p30, v30, (float) dgf[s30 - 1]);
        double[] near = SolveCore.optimize(model, tail30, budget, 20.0, 0.0, new AtomicBoolean(false), null);
        System.out.printf("step1 [30,54) near-feasible: viol=%.3e objX=%.5f%n", viol(tail30, near), objX(tail30, near));

        // Extract the t42 seam that [30,42) of the near solution reaches.
        JumpPhysicsInputs sc30 = tail30.asScenario();
        double[] gf30 = sc30.toGameFacings(Angles.wrapAll(near));
        ForwardPath pth30 = model.forward(sc30, gf30);
        int loc42 = s42 - s30;
        Vec3dCore p42 = new Vec3dCore(pth30.posX[loc42], pth30.posY[loc42], pth30.posZ[loc42]);
        Vec3dCore v42 = new Vec3dCore(pth30.velX[loc42], pth30.velY[loc42], pth30.velZ[loc42]);
        System.out.printf("reached t42 seam pos=(%.4f,%.4f) vel=(%.5f,%.5f)  [proven t42 pos=(9.7000,6.5047) vel=(-0.16305,0.19379)]%n",
                p42.x, p42.z, v42.x, v42.z);

        // Diagnostic: is the REACHED t42 seam landable at all? Forward the proven [42,54) game-facings from it.
        JumpSpec tail42 = sliced(s42, n, p42, v42, (float) gf30[loc42 - 1]);
        double[] provTail42Gf = new double[n - s42];
        System.arraycopy(dgfFull, s42, provTail42Gf, 0, n - s42);
        System.out.printf("diag: proven [42,54) game-facings from REACHED seam -> viol=%.3e (is the reached seam landable?)%n",
                violGf(tail42, provTail42Gf));

        // Step 2: SolveCore [42,54) from that t42 seam, cold and warm (from near's own [42,54) tail).
        double[] closeCold = SolveCore.optimize(model, tail42, budget, 20.0, 0.0, new AtomicBoolean(false), null);
        double[] nearTail42 = new double[n - s42];
        System.arraycopy(near, loc42, nearTail42, 0, n - s42);
        double[] closeWarm = SolveCore.optimize(model, tail42, budget, 8.0, 0.0, new AtomicBoolean(false), Angles.wrapAll(nearTail42));
        double vCold = viol(tail42, closeCold), vWarm = viol(tail42, closeWarm);
        System.out.printf("step2 [42,54): cold viol=%.3e | warm viol=%.3e  %s%n",
                vCold, vWarm, (Math.min(vCold, vWarm) <= 0.0) ? "*** landing byte-exact ***" : "infeasible");
        double[] close = vWarm <= vCold ? closeWarm : closeCold;

        // Combine in GAME-FACING space (no re-accumulation across the splice).
        double[] closeGf = tail42.asScenario().toGameFacings(Angles.wrapAll(close));
        double[] combGf = new double[n - s30];
        System.arraycopy(gf30, 0, combGf, 0, loc42);
        System.arraycopy(closeGf, 0, combGf, loc42, closeGf.length);
        double combViol = violGf(tail30, combGf);
        System.out.printf("COMBINED [30,54) (game-facing chain): viol=%.3e  %s%n",
                combViol, combViol <= 0.0 ? "*** TAIL SOLVED byte-exact ***" : "not closed");
        ForwardPath cp = model.forward(tail30.asScenario(), combGf);
        System.out.println("  binding constraints in the combined tail (slack > 1e-9):");
        for (JumpConstraint c : tail30.constraints) {
            double slk = JumpConstraintCompiler.slack(c, combGf, cp);
            if (slk > 1.0e-9) System.out.printf("    %s t1=%d %s rhs=%.4f slack=%.3e%n", c.mode, c.t1 + s30, c.cmp, c.rhs, slk);
        }
    }

    private JumpSpec sliced(int a, int c, Vec3dCore pos, Vec3dCore vel, float yaw) {
        JumpPhysicsInputs win = sliceScenario(full, a, c, pos, vel, yaw);
        List<JumpConstraint> cons = sliceConstraints(fullSpec, a, c);
        Objective obj = new Objective(fullSpec.objective.axis, fullSpec.objective.sense, c - a);
        return new JumpSpec(win, cons, obj);
    }

    private double viol(JumpSpec spec, double[] yawsAbs) {
        if (yawsAbs == null) return Double.NaN;
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, model.forward(sc, gf));
    }

    private double violGf(JumpSpec spec, double[] gf) {
        JumpPhysicsInputs sc = spec.asScenario();
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, model.forward(sc, gf));
    }

    private double objX(JumpSpec spec, double[] yawsAbs) {
        if (yawsAbs == null) return Double.NaN;
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        return model.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
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
