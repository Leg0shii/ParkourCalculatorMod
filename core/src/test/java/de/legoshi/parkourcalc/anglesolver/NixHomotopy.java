package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.CmaesJumpHarness;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverRunResult;
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

public class NixHomotopy {

    private ExactJumpModel model;
    private JumpSpec fullSpec;
    private JumpPhysicsInputs full;

    @Test
    public void close() throws Exception {
        String path = System.getenv("PKC_HOMO_FILE");
        org.junit.Assume.assumeTrue("set PKC_HOMO_FILE", path != null && !path.isEmpty());
        int seam = Integer.parseInt(System.getenv().getOrDefault("PKC_HOMO_SEAM", "30"));
        double eps0 = Double.parseDouble(System.getenv().getOrDefault("PKC_HOMO_EPS0", "1e-3"));
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

        System.out.printf(Locale.ROOT, "=== NixHomotopy %s seam=%d eps0=%.1e ===%n", new File(path).getName(), seam, eps0);
        Vec3dCore p = new Vec3dCore(dp.posX[seam], dp.posY[seam], dp.posZ[seam]);
        Vec3dCore v = new Vec3dCore(dp.velX[seam], dp.velY[seam], dp.velZ[seam]);
        System.out.printf(Locale.ROOT, "proven t%d seam pos=(%.5f,%.5f) vel=(%.6f,%.6f)%n", seam, p.x, p.z, v.x, v.z);

        JumpSpec tail = sliced(seam, n, p, v, (float) dgf[seam - 1]);
        double[] result;
        if ("1".equals(System.getenv("PKC_HOMO_SKIPLADDER"))) {
            long tc = System.nanoTime();
            result = SolveCore.optimize(model, tail, new SolveCore.Budget(256, 100000, 8, BucketAscentPolish.FAST),
                    20.0, 0.0, new AtomicBoolean(false), null);
            System.out.printf(Locale.ROOT, "direct cold: viol=%.3e (%.1fs)%n", viol(tail, result), sec(tc));
        } else {
            result = homotopy(tail, eps0);
        }
        if (result == null) {
            System.out.println("HOMOTOPY FAILED (no relaxed-feasible entry)");
            return;
        }
        double ladderViol = viol(tail, result);
        System.out.printf(Locale.ROOT, "after ladder: real-spec viol=%.3e objX=%.6f%n", ladderViol, objX(tail, result));
        if (ladderViol > 0.0) {
            long tr = System.nanoTime();
            double[] rep = bucketRepair(tail, result);
            System.out.printf(Locale.ROOT, "bucket repair: %.3e -> %.3e (%.1fs)%n", ladderViol, viol(tail, rep), sec(tr));
            if (viol(tail, rep) < ladderViol) result = rep;
        }
        double finalViol = viol(tail, result);
        System.out.printf(Locale.ROOT, "FINAL real-spec viol=%.3e objX=%.6f  %s%n",
                finalViol, objX(tail, result), finalViol <= 0.0 ? "*** CLOSED BYTE-EXACT ***" : "not closed");
        if (finalViol <= 0.0) {
            double[] pol = BucketAscentPolish.polish(model, tail, Angles.wrapAll(result), BucketAscentPolish.THOROUGH, new AtomicBoolean(false));
            System.out.printf(Locale.ROOT, "post-polish viol=%.3e objX=%.6f%n", viol(tail, pol), objX(tail, pol));
        }
    }

    private double[] homotopy(JumpSpec spec, double eps0) {
        long t0 = System.nanoTime();
        AtomicBoolean cancel = new AtomicBoolean(false);
        SolveCore.Budget entry = new SolveCore.Budget(256, 100000, 8, BucketAscentPolish.FAST);
        double eps = eps0;
        double[] y = null;
        for (int i = 0; i < 3 && y == null; i++, eps *= 4.0) {
            JumpSpec relaxed = relax(spec, eps);
            double[] cand = SolveCore.optimize(model, relaxed, entry, 20.0, 0.0, cancel, null);
            double cv = cand == null ? Double.NaN : viol(relaxed, cand);
            System.out.printf(Locale.ROOT, "entry eps=%.2e viol=%.3e (%.1fs)%n", eps, cv, sec(t0));
            if (cand != null && cv <= 0.0) y = cand;
        }
        if (y == null) return null;
        eps /= 4.0;
        double epsGood = eps;
        int rung = 0;
        while (epsGood > 0.0 && rung < 80) {
            double epsNext = epsGood <= 2.0e-6 ? 0.0 : epsGood * 0.5;
            int refine = 0;
            while (true) {
                rung++;
                JumpSpec sp = relax(spec, epsNext);
                double before = viol(sp, y);
                if (before <= 0.0) break;
                double[] fixed = repair(sp, y, cancel);
                double after = fixed == null ? before : viol(sp, fixed);
                if (after <= 0.0) {
                    y = fixed;
                    break;
                }
                if (fixed != null && after < before) y = fixed;
                refine++;
                if (refine > 6) {
                    System.out.printf(Locale.ROOT, "STALL at eps=%.3e (best residual %.3e, %.1fs)%n", epsNext, Math.min(before, after), sec(t0));
                    return y;
                }
                epsNext = 0.5 * (epsGood + epsNext);
            }
            epsGood = epsNext;
            System.out.printf(Locale.ROOT, "  rung %d ok eps=%.3e (%.1fs)%n", rung, epsGood, sec(t0));
        }
        return y;
    }

    private double[] repair(JumpSpec sp, double[] warm, AtomicBoolean cancel) {
        double[] best = null;
        double bestV = Double.POSITIVE_INFINITY;
        for (double sigma : new double[]{0.3, 1.0, 3.0}) {
            SolverRunResult rr = new CmaesJumpHarness(1.0e7, 1.0e7, sigma, 60000, true).solve(model, sp, Angles.wrapAll(warm.clone()), cancel);
            double vv = viol(sp, rr.yawAbsDeg);
            if (vv < bestV) {
                bestV = vv;
                best = rr.yawAbsDeg;
            }
            if (vv <= 0.0) return rr.yawAbsDeg;
        }
        return best;
    }

    private double[] bucketRepair(JumpSpec spec, double[] start) {
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        JumpPhysicsInputs sc = spec.asScenario();
        double[] y = Angles.wrapAll(start.clone());
        double best = maxSlack(c, sc, y);
        double[][] b1 = {{0.05, 0.001}, {0.012, 0.0002}, {0.003, 0.00005}, {0.0008, 0.00001}};
        double[][] b2 = {{0.02, 0.0008}, {0.006, 0.0002}, {0.0015, 0.00004}};
        int n = y.length;
        for (int round = 0; round < 24 && best > 0.0; round++) {
            boolean moved = false;
            for (double[] r : b1) {
                for (int t = 0; t < n && best > 0.0; t++) {
                    double orig = y[t], by = orig, bo = best;
                    for (double d = -r[0]; d <= r[0] + 1e-12; d += r[1]) {
                        y[t] = orig + d;
                        double s = maxSlack(c, sc, y);
                        if (s < bo) { bo = s; by = y[t]; }
                    }
                    y[t] = by;
                    if (bo < best) { best = bo; moved = true; }
                }
            }
            if (best <= 0.0) break;
            for (double[] r : b2) {
                for (int i = 0; i < n && best > 0.0; i++) {
                    for (int j = i + 1; j <= Math.min(n - 1, i + 3); j++) {
                        double oi = y[i], oj = y[j], bi = oi, bj = oj, bo = best;
                        for (double di = -r[0]; di <= r[0] + 1e-12; di += r[1]) {
                            y[i] = oi + di;
                            for (double dj = -r[0]; dj <= r[0] + 1e-12; dj += r[1]) {
                                y[j] = oj + dj;
                                double s = maxSlack(c, sc, y);
                                if (s < bo) { bo = s; bi = y[i]; bj = y[j]; }
                            }
                        }
                        y[i] = bi; y[j] = bj;
                        if (bo < best) { best = bo; moved = true; }
                    }
                }
            }
            if (!moved) break;
        }
        return y;
    }

    private double maxSlack(JumpConstraintCompiler.Compiled c, JumpPhysicsInputs sc, double[] absYaws) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(absYaws));
        ForwardPath pr = model.forward(sc, gf);
        double m = Double.NEGATIVE_INFINITY;
        for (JumpConstraint cc : c.ineq) m = Math.max(m, JumpConstraintCompiler.slack(cc, gf, pr));
        for (JumpConstraint cc : c.eq) m = Math.max(m, Math.abs(JumpConstraintCompiler.evaluate(cc, gf, pr)));
        return m;
    }

    private JumpSpec relax(JumpSpec spec, double eps) {
        if (eps <= 0.0) return spec;
        List<JumpConstraint> out = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) {
            if (c.cmp == JumpConstraint.Cmp.GE) out.add(new JumpConstraint(c.mode, c.t1, c.t2, c.op, c.cmp, c.rhs - eps, c.name));
            else if (c.cmp == JumpConstraint.Cmp.LE) out.add(new JumpConstraint(c.mode, c.t1, c.t2, c.op, c.cmp, c.rhs + eps, c.name));
            else out.add(c);
        }
        return new JumpSpec(spec.asScenario(), out, spec.objective);
    }

    private double viol(JumpSpec spec, double[] yawsAbs) {
        if (yawsAbs == null) return Double.NaN;
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, model.forward(sc, gf));
    }

    private double objX(JumpSpec spec, double[] yawsAbs) {
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        return model.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
    }

    private static double sec(long t0) {
        return (System.nanoTime() - t0) / 1.0e9;
    }

    private JumpSpec sliced(int a, int c, Vec3dCore pos, Vec3dCore vel, float yaw) {
        JumpPhysicsInputs win = sliceScenario(full, a, c, pos, vel, yaw);
        List<JumpConstraint> cons = sliceConstraints(fullSpec, a, c);
        Objective obj = new Objective(fullSpec.objective.axis, fullSpec.objective.sense, c - a);
        return new JumpSpec(win, cons, obj);
    }

    private static JumpPhysicsInputs sliceScenario(JumpPhysicsInputs sc, int a, int c, Vec3dCore pos, Vec3dCore vel, float yaw) {
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
        p.forwardInputPerTick = sliceFloat(sc.forwardInputPerTick, a, len, 0.98F);
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

    private static boolean[] sliceBool(boolean[] x, int f, int len) { if (x == null) return null; boolean[] o = new boolean[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length && x[f + i]; return o; }
    private static int[] sliceInt(int[] x, int f, int len) { if (x == null) return null; int[] o = new int[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : 0; return o; }
    private static double[] sliceDouble(double[] x, int f, int len) { if (x == null) return null; double[] o = new double[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : Double.NaN; return o; }
    private static float[] sliceFloat(float[] x, int f, int len, float d) { if (x == null) return null; float[] o = new float[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : d; return o; }
}
