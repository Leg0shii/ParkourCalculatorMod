package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
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

public class NixTailClose {

    private static final double[] STEPS = {1.0, 0.3, 0.1, 0.03, 0.01, 0.003, 0.001, 3.0e-4, 1.0e-4, 3.0e-5, 1.0e-5, 3.0e-6};

    @Test
    public void close() throws Exception {
        String path = System.getenv("PKC_CLOSE_FILE");
        org.junit.Assume.assumeTrue("set PKC_CLOSE_FILE", path != null && !path.isEmpty());
        String seamsEnv = System.getenv().getOrDefault("PKC_CLOSE_SEAMS", "42,30,28");

        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs full = spec.asScenario();
        int n = full.numTicks;

        double[] dyaw = new double[n];
        for (int k = 0; k < n; k++) dyaw[k] = file.debug.get(k + 1).yaw;
        double[] dgf = full.toGameFacings(Angles.wrapAll(dyaw));
        ForwardPath dp = model.forward(full, dgf);

        System.out.printf("=== NixTailClose %s (closer A = byte-exact coord-descent min-violation) ===%n",
                new File(path).getName());

        for (String s : seamsEnv.split(",")) {
            int a = Integer.parseInt(s.trim());
            JumpPhysicsInputs win = sliceScenario(full, a, n,
                    new Vec3dCore(dp.posX[a], dp.posY[a], dp.posZ[a]),
                    new Vec3dCore(dp.velX[a], dp.velY[a], dp.velZ[a]), (float) dgf[a - 1]);
            List<JumpConstraint> cons = sliceConstraints(spec, a, n);
            Objective obj = new Objective(spec.objective.axis, spec.objective.sense, n - a);
            JumpSpec tail = new JumpSpec(win, cons, obj);

            double provTailViol = viol(model, tail, subYaws(dyaw, a, n));
            System.out.printf("%n--- seam=%d  [%d,%d)  %d ticks  #cons=%d  provenTailViol=%.3e ---%n",
                    a, a, n, n - a, cons.size(), provTailViol);

            double[] seed = BoundPrunedRecovery.solve(model, tail, 3.0e-4, new AtomicBoolean(false), 25_000_000_000L, Double.NaN);
            if (seed == null) {
                seed = BoundPrunedRecovery.solve(model, tail, 1.0e-3, new AtomicBoolean(false), 25_000_000_000L, Double.NaN);
            }
            if (seed != null) {
                System.out.printf("  BnB near-feasible seed: viol=%.3e objX=%.5f%n", viol(model, tail, seed), objX(model, tail, seed));
            } else {
                System.out.printf("  BnB gave no near-feasible seed (both feasTol NULL)%n");
            }

            SolveCore.Budget budget = new SolveCore.Budget(256, 100000, 64, BucketAscentPolish.THOROUGH);

            long t0 = System.nanoTime();
            double[] cold = SolveCore.optimize(model, tail, budget, 20.0, 0.0, new AtomicBoolean(false), null);
            long msCold = (System.nanoTime() - t0) / 1_000_000L;
            double cvCold = cold == null ? Double.NaN : viol(model, tail, cold);
            System.out.printf("  SolveCore COLD (custom 256/100k, feasTol=0): viol=%.3e objX=%.5f in %dms  %s%n",
                    cvCold, cold == null ? Double.NaN : objX(model, tail, cold), msCold,
                    (cold != null && cvCold <= 0.0) ? "*** FEASIBLE byte-exact ***" : "infeasible");

            if (seed != null) {
                long t1 = System.nanoTime();
                double[] warm = SolveCore.optimize(model, tail, budget, 8.0, 0.0, new AtomicBoolean(false), Angles.wrapAll(seed));
                long msWarm = (System.nanoTime() - t1) / 1_000_000L;
                double cvWarm = warm == null ? Double.NaN : viol(model, tail, warm);
                System.out.printf("  SolveCore WARM (from BnB seed, sigma=8): viol=%.3e objX=%.5f in %dms  %s%n",
                        cvWarm, warm == null ? Double.NaN : objX(model, tail, warm), msWarm,
                        (warm != null && cvWarm <= 0.0) ? "*** FEASIBLE byte-exact ***" : "infeasible");
            }
        }
    }

    private double[] coordDescent(ExactJumpModel model, JumpSpec tail, double[] seed) {
        int n = tail.asScenario().numTicks;
        double[] cur = Angles.wrapAll(seed.clone());
        double best = viol(model, tail, cur);
        for (double step : STEPS) {
            boolean improved = true;
            int guard = 0;
            while (improved && best > 0.0 && guard++ < 200) {
                improved = false;
                for (int t = 0; t < n; t++) {
                    for (int dir = -1; dir <= 1; dir += 2) {
                        double[] cand = cur.clone();
                        cand[t] = Angles.wrap(cand[t] + dir * step);
                        double v = viol(model, tail, cand);
                        if (v < best - 1.0e-15) {
                            cur = cand;
                            best = v;
                            improved = true;
                        }
                    }
                }
            }
            if (best <= 0.0) break;
        }
        return cur;
    }

    private static double viol(ExactJumpModel model, JumpSpec tail, double[] yawsAbs) {
        JumpPhysicsInputs sc = tail.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        return JumpConstraintCompiler.compile(tail).maxViolation(gf, model.forward(sc, gf));
    }

    private static double objX(ExactJumpModel model, JumpSpec tail, double[] yawsAbs) {
        JumpPhysicsInputs sc = tail.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        return model.forward(sc, gf).getPos(tail.objective.tick, tail.objective.axis);
    }

    private static double[] subYaws(double[] dyaw, int a, int n) {
        double[] o = new double[n - a];
        System.arraycopy(dyaw, a, o, 0, n - a);
        return o;
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
