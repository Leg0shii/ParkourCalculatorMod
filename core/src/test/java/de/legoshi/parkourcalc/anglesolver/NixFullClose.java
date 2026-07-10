package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.CmaesJumpHarness;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverRunResult;
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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class NixFullClose {

    private ExactJumpModel model;
    private JumpSpec fullSpec;
    private JumpPhysicsInputs full;

    @Test
    public void close() throws Exception {
        String path = System.getenv("PKC_FC_FILE");
        org.junit.Assume.assumeTrue("set PKC_FC_FILE", path != null && !path.isEmpty());
        boolean cold = "cold".equals(System.getenv().getOrDefault("PKC_FC_MODE", "warm"));
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
        int n = full.numTicks;
        StartBox box = full.startBox;
        System.out.printf(Locale.ROOT, "=== NixFullClose %s n=%d cons=%d box=%s mode=%s ===%n",
                new File(path).getName(), n, fullSpec.constraints.size(),
                box == null ? "null" : box.label(), cold ? "cold" : "warm");
        org.junit.Assume.assumeTrue("need free startBox", box != null && box.startFree());

        double[] warm = null;
        if (!cold && file.angleSolver != null && file.angleSolver.result != null
                && file.angleSolver.result.yaws != null && !file.angleSolver.result.yaws.isEmpty()) {
            warm = new double[n];
            java.util.Arrays.fill(warm, Double.NaN);
            for (SaveFile.Yaw yw : file.angleSolver.result.yaws) {
                int t = yw.tick - 1;
                if (t >= 0 && t < n) warm[t] = yw.yaw;
            }
            for (int t = 0; t < n; t++) if (Double.isNaN(warm[t])) warm[t] = full.startYaw;
            System.out.printf(Locale.ROOT, "warm incumbent (saved result): freeViol=%.4e%n", violFree(fullSpec, warm));
        }

        double[] y = homotopy(fullSpec, warm, 1.0e-2, t0);
        if (y == null) {
            System.out.println("HOMOTOPY FAILED (no relaxed-feasible entry)");
            return;
        }
        double fv = violFree(fullSpec, y);
        System.out.printf(Locale.ROOT, "after ladder+repair: freeViol=%.3e (%.1fs)%n", fv, sec(t0));

        double[] p0 = FreeStartSolve.recoverStart(model, fullSpec, y);
        if (p0 == null) {
            System.out.println("no recoverStart");
            return;
        }
        double pv = FreeStartSolve.violationAt(model, fullSpec, y, p0[0], p0[1]);
        System.out.printf(Locale.ROOT, "pinned at (%.6f,%.6f): viol=%.3e%n", p0[0], p0[1], pv);
        if (pv > 0.0) {
            JumpSpec pinned = specAt(p0[0], p0[1]);
            double[] rep = bucketDescent(pinned, y, false);
            double rv = viol(pinned, rep);
            System.out.printf(Locale.ROOT, "pinned bucket repair: %.3e -> %.3e%n", pv, rv);
            if (rv < pv) { y = rep; pv = rv; }
        }
        JumpSpec pinned = specAt(p0[0], p0[1]);
        double obj = objX(pinned, y);
        System.out.printf(Locale.ROOT, "FINAL full-route viol=%.3e objX=%.7f start=(%.6f,%.6f) (%.1fs) %s%n",
                pv, obj, p0[0], p0[1], sec(t0), pv <= 0.0 ? "*** NIX FULL ROUTE SOLVED FROM t1 ***" : "not closed");
        if (pv <= 0.0) {
            double[] gf = pinned.asScenario().toGameFacings(Angles.wrapAll(y));
            for (int t = 0; t < n; t++) System.out.printf(Locale.ROOT, "t%02d gf=%.6f%n", t, gf[t]);
        }
    }

    private JumpSpec specAt(double p0x, double p0z) {
        JumpPhysicsInputs p = new JumpPhysicsInputs(full.numTicks);
        p.startPos = new Vec3dCore(p0x, full.startPos.y, p0z);
        p.initialVelocity = full.initialVelocity;
        p.startYaw = full.startYaw;
        p.strafeSign = full.strafeSign;
        p.jumpPerTick = full.jumpPerTick;
        p.strafePerTick = full.strafePerTick;
        p.yawLockedPerTick = full.yawLockedPerTick;
        p.speedAmplifier = full.speedAmplifier;
        p.slipPerTick = full.slipPerTick;
        p.sprintPerTick = full.sprintPerTick;
        p.forwardInputPerTick = full.forwardInputPerTick;
        p.strafeInputPerTick = full.strafeInputPerTick;
        p.startBox = StartBox.pinned(p0x, p0z, full.initialVelocity.x, full.initialVelocity.z);
        return new JumpSpec(p, fullSpec.constraints, fullSpec.objective);
    }

    private double[] homotopy(JumpSpec spec, double[] warm, double eps0, long t0) {
        AtomicBoolean cancel = new AtomicBoolean(false);
        SolveCore.Budget entryBudget = new SolveCore.Budget(192, 100000, 8, BucketAscentPolish.FAST);
        double eps = eps0;
        double[] y = null;
        for (int i = 0; i < 3 && y == null; i++, eps *= 4.0) {
            JumpSpec relaxed = relax(spec, eps);
            double[] cand = warm != null && violFree(relaxed, warm) <= 0.0 ? warm
                    : SolveCore.optimize(model, relaxed, entryBudget, 20.0, 0.0, cancel, warm);
            double cv = cand == null ? Double.NaN : violFree(relaxed, cand);
            System.out.printf(Locale.ROOT, "entry eps=%.2e viol=%.3e (%.1fs)%n", eps, cv, sec(t0));
            if (cand != null && cv <= 0.0) y = cand;
        }
        if (y == null) return null;
        eps /= 4.0;
        double epsGood = eps;
        int rung = 0;
        while (epsGood > 0.0 && rung < 120) {
            double epsNext = epsGood <= 2.0e-6 ? 0.0 : epsGood * 0.5;
            int refine = 0;
            while (true) {
                rung++;
                JumpSpec sp = relax(spec, epsNext);
                double before = violFree(sp, y);
                if (before <= 0.0) break;
                double[] fixed = repair(sp, y, cancel);
                double after = fixed == null ? before : violFree(sp, fixed);
                if (after <= 0.0) { y = fixed; break; }
                if (fixed != null && after < before) y = fixed;
                refine++;
                if (refine > 6) {
                    double[] rep = bucketDescent(sp, y, true);
                    if (violFree(sp, rep) <= 0.0) { y = rep; break; }
                    System.out.printf(Locale.ROOT, "  stall at eps=%.3e best=%.3e (%.1fs)%n", epsNext, violFree(sp, y), sec(t0));
                    return y;
                }
                epsNext = 0.5 * (epsGood + epsNext);
            }
            epsGood = epsNext;
            if (rung % 5 == 0 || epsGood == 0.0) {
                System.out.printf(Locale.ROOT, "  rung %d eps=%.3e (%.1fs)%n", rung, epsGood, sec(t0));
            }
        }
        double fv = violFree(spec, y);
        if (fv > 0.0) {
            double[] rep = bucketDescent(spec, y, true);
            if (violFree(spec, rep) < fv) y = rep;
        }
        return y;
    }

    private double[] repair(JumpSpec sp, double[] warm, AtomicBoolean cancel) {
        double[] best = null;
        double bestV = Double.POSITIVE_INFINITY;
        for (double sigma : new double[]{0.3, 1.0, 3.0}) {
            SolverRunResult rr = new CmaesJumpHarness(1.0e7, 1.0e7, sigma, 60000, true).solve(model, sp, Angles.wrapAll(warm.clone()), cancel);
            double vv = violFree(sp, rr.yawAbsDeg);
            if (vv < bestV) { bestV = vv; best = rr.yawAbsDeg; }
            if (vv <= 0.0) return rr.yawAbsDeg;
        }
        return best;
    }

    private double[] bucketDescent(JumpSpec spec, double[] start, boolean free) {
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        JumpPhysicsInputs sc = spec.asScenario();
        double[] y = Angles.wrapAll(start.clone());
        double best = slackOf(spec, c, sc, y, free);
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
                        double s = slackOf(spec, c, sc, y, free);
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
                                double s = slackOf(spec, c, sc, y, free);
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

    private double slackOf(JumpSpec spec, JumpConstraintCompiler.Compiled c, JumpPhysicsInputs sc, double[] absYaws, boolean free) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(absYaws));
        ForwardPath pr = model.forward(sc, gf);
        double m = Double.NEGATIVE_INFINITY;
        if (free && sc.startBox != null && sc.startBox.startFree()) {
            double[] d = FreeStartSolve.bestTranslate(spec, gf, pr, sc.startBox);
            for (JumpConstraint cc : c.ineq) m = Math.max(m, JumpConstraintCompiler.translatedSlack(cc, gf, pr, d[0], d[1]));
            for (JumpConstraint cc : c.eq) m = Math.max(m, Math.abs(JumpConstraintCompiler.translatedEvaluate(cc, gf, pr, d[0], d[1])));
        } else {
            for (JumpConstraint cc : c.ineq) m = Math.max(m, JumpConstraintCompiler.slack(cc, gf, pr));
            for (JumpConstraint cc : c.eq) m = Math.max(m, Math.abs(JumpConstraintCompiler.evaluate(cc, gf, pr)));
        }
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

    private double violFree(JumpSpec spec, double[] yawsAbs) {
        if (yawsAbs == null) return Double.NaN;
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        JumpPhysicsInputs sc = spec.asScenario();
        return Math.max(0.0, slackOf(spec, c, sc, yawsAbs, true));
    }

    private double objX(JumpSpec spec, double[] yawsAbs) {
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        return model.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
    }

    private static double sec(long t0) {
        return (System.nanoTime() - t0) / 1.0e9;
    }
}
