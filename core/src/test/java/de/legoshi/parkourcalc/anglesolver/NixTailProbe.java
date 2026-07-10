package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
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

public class NixTailProbe {

    private static final long MONO_BUDGET = 30_000_000_000L;
    private static final long TAIL_BUDGET = 25_000_000_000L;
    private static final long BAND_BUDGET = 6_000_000_000L;

    @Test
    public void relaxProbe() throws Exception {
        String path = System.getenv("PKC_RELAX_FILE");
        org.junit.Assume.assumeTrue("set PKC_RELAX_FILE", path != null && !path.isEmpty());
        int tailJumps = Integer.parseInt(System.getenv().getOrDefault("PKC_RELAX_TAILJUMPS", "2"));
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int[] bounds = jumpBoundaries(sc);
        int jumps = bounds.length - 1;
        double[] dyaw = new double[n];
        for (int k = 0; k < n; k++) dyaw[k] = file.debug.get(k + 1).yaw;
        double[] dgf = sc.toGameFacings(Angles.wrapAll(dyaw));
        ForwardPath dp = model.forward(sc, dgf);
        int a = bounds[jumps - tailJumps];
        Vec3dCore pos = new Vec3dCore(dp.posX[a], dp.posY[a], dp.posZ[a]);
        Vec3dCore vel = new Vec3dCore(dp.velX[a], dp.velY[a], dp.velZ[a]);
        JumpPhysicsInputs win = sliceScenario(sc, a, n, pos, vel, (float) dgf[a - 1]);
        List<JumpConstraint> cons = sliceConstraints(spec, a, n);
        Objective obj = new Objective(spec.objective.axis, spec.objective.sense, n - a);
        JumpSpec tailSpec = new JumpSpec(win, cons, obj);
        System.out.printf("=== RELAX PROBE %s: %d-jump tail [%d,%d) #cons=%d ===%n",
                new File(path).getName(), tailJumps, a, n, cons.size());
        for (double ft : new double[]{0.0, 1.0e-4, 3.0e-4, 5.0e-4, 1.0e-3, 2.0e-3}) {
            long t0 = System.nanoTime();
            double[] yaws = BoundPrunedRecovery.solve(model, tailSpec, ft, new AtomicBoolean(false), 25_000_000_000L, Double.NaN);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            if (yaws == null) {
                System.out.printf("  feasTol=%.1e -> NULL in %dms%n", ft, ms);
                continue;
            }
            double[] gf = win.toGameFacings(Angles.wrapAll(yaws));
            ForwardPath p = model.forward(win, gf);
            double viol = JumpConstraintCompiler.compile(tailSpec).maxViolation(gf, p);
            System.out.printf("  feasTol=%.1e -> objX=%.6f trueViol=%.3e in %dms%n",
                    ft, p.getPos(obj.tick, obj.axis), viol, ms);
        }
    }

    @Test
    public void probe() throws Exception {
        String files = System.getenv("PKC_PROBE_FILES");
        org.junit.Assume.assumeTrue("set PKC_PROBE_FILES", files != null && !files.isEmpty());
        for (String path : files.split(";")) {
            if (path.trim().isEmpty()) continue;
            try {
                runOne(path.trim());
            } catch (Exception e) {
                System.out.printf("ROUTE ERROR %s: %s%n", path, e);
                e.printStackTrace();
            }
            System.out.println();
        }
    }

    private void runOne(String path) throws Exception {
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;

        int[] bounds = jumpBoundaries(sc);
        int jumps = bounds.length - 1;
        StringBuilder pat = new StringBuilder();
        for (int t = 0; t < n; t++) {
            boolean grounded = !Double.isNaN(sc.slipAt(t));
            boolean jumpFires = sc.jumpAt(t) && grounded;
            pat.append(jumpFires ? 'J' : (grounded ? 'g' : '.'));
        }
        System.out.println("================================================================");
        System.out.printf("=== %s%n", new File(path).getName());
        System.out.printf("n=%d jumps=%d obj=%s/%s@%d #cons=%d%n",
                n, jumps, spec.objective.axis, spec.objective.sense, spec.objective.tick, spec.constraints.size());
        System.out.printf("pattern: %s%n", pat);
        StringBuilder bs = new StringBuilder();
        for (int b : bounds) bs.append(b).append(' ');
        System.out.printf("jump boundaries: %s%n", bs.toString().trim());

        if (file.debug == null || file.debug.size() < n + 1) {
            System.out.println("NO USABLE DEBUG BLOCK (need >= n+1 entries); cannot extract proven seams. SKIP.");
            return;
        }
        double[] dyaw = new double[n];
        for (int k = 0; k < n; k++) dyaw[k] = file.debug.get(k + 1).yaw;
        double[] dgf = sc.toGameFacings(Angles.wrapAll(dyaw));
        ForwardPath dp = model.forward(sc, dgf);
        double maxDiff = 0.0;
        for (int t = 0; t <= n; t++) {
            double[] dpos = file.debug.get(t).pos;
            maxDiff = Math.max(maxDiff, Math.abs(dp.posX[t] - dpos[0]));
            maxDiff = Math.max(maxDiff, Math.abs(dp.posZ[t] - dpos[2]));
        }
        double provViol = JumpConstraintCompiler.compile(spec).maxViolation(dgf, dp);
        System.out.printf("proven-run model-vs-sim maxPosDiff=%.3e  fullConstraintViol=%.5e  provenObjX=%.5f%n",
                maxDiff, provViol, dp.getPos(spec.objective.tick, spec.objective.axis));

        System.out.println("--- Experiment B: largest BnB-solvable tail (seeded at proven seam) ---");
        System.out.println("  provTail = forward the PROVEN tail yaws through the slice (proves a feasible tail EXISTS)");
        System.out.println("  tailJumps  seamTick  seamVel(vx,vz)      #cons  provTail(viol,objX)     BnB result");
        int largestFeasibleK = -1;
        for (int k = 1; k <= jumps; k++) {
            int a = bounds[jumps - k];
            long budget = (a == 0) ? MONO_BUDGET : TAIL_BUDGET;
            TailResult tr = solveTail(model, spec, sc, dp, dgf, dyaw, a, n, budget);
            String vel = a == 0 ? String.format("(%.5f,%.5f)", sc.initialVelocity.x, sc.initialVelocity.z)
                    : String.format("(%.5f,%.5f)", dp.velX[a], dp.velZ[a]);
            System.out.printf("  %s%-9d  %-8d  %-18s  %-5d  %-22s  %s%n",
                    a == 0 ? "[MONO] " : "       ", k, a, vel, tr.numCons, tr.provDesc, tr.desc);
            if (tr.feasible) largestFeasibleK = k;
        }
        System.out.printf("=> largest BnB-solvable tail = %s jumps%s%n",
                largestFeasibleK < 0 ? "NONE" : String.valueOf(largestFeasibleK),
                largestFeasibleK == jumps ? " (MONOLITHIC feasible; no decomposition needed)" : "");

        int seamK = Math.min(largestFeasibleK < 0 ? 1 : largestFeasibleK, jumps - 1);
        if (jumps >= 2 && seamK >= 1) {
            int a = bounds[jumps - seamK];
            System.out.printf("--- Experiment C: seam-velocity band at tail seam tick %d (%d-jump tail), proven pos fixed ---%n",
                    a, seamK);
            bandSweep(model, spec, sc, dp, dgf, a, n);
        } else {
            System.out.println("--- Experiment C: skipped (single-jump route, no interior seam) ---");
        }
    }

    private static final class TailResult {
        final boolean feasible;
        final int numCons;
        final String desc;
        final String provDesc;
        TailResult(boolean feasible, int numCons, String desc, String provDesc) {
            this.feasible = feasible;
            this.numCons = numCons;
            this.desc = desc;
            this.provDesc = provDesc;
        }
    }

    private TailResult solveTail(ExactJumpModel model, JumpSpec full, JumpPhysicsInputs sc,
                                 ForwardPath dp, double[] dgf, double[] dyaw, int a, int n, long budget) {
        JumpSpec tailSpec;
        String provDesc;
        if (a == 0) {
            tailSpec = full;
            double[] gf0 = sc.toGameFacings(Angles.wrapAll(dyaw));
            ForwardPath pp = model.forward(sc, gf0);
            double pv = JumpConstraintCompiler.compile(full).maxViolation(gf0, pp);
            provDesc = String.format("(%.2e,%.5f)", pv, pp.getPos(full.objective.tick, full.objective.axis));
        } else {
            Vec3dCore pos = new Vec3dCore(dp.posX[a], dp.posY[a], dp.posZ[a]);
            Vec3dCore vel = new Vec3dCore(dp.velX[a], dp.velY[a], dp.velZ[a]);
            JumpPhysicsInputs win = sliceScenario(sc, a, n, pos, vel, (float) dgf[a - 1]);
            List<JumpConstraint> cons = sliceConstraints(full, a, n);
            Objective obj = new Objective(full.objective.axis, full.objective.sense, n - a);
            tailSpec = new JumpSpec(win, cons, obj);
            double[] provTail = new double[n - a];
            System.arraycopy(dyaw, a, provTail, 0, n - a);
            double[] pgf = win.toGameFacings(Angles.wrapAll(provTail));
            ForwardPath pp = model.forward(win, pgf);
            double pv = JumpConstraintCompiler.compile(tailSpec).maxViolation(pgf, pp);
            provDesc = String.format("(%.2e,%.5f)", pv, pp.getPos(obj.tick, obj.axis));
        }
        long t0 = System.nanoTime();
        AtomicBoolean cancel = new AtomicBoolean(false);
        double[] yaws = BoundPrunedRecovery.solve(model, tailSpec, 0.0, cancel, budget, Double.NaN);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        JumpPhysicsInputs tsc = tailSpec.asScenario();
        if (yaws == null) {
            return new TailResult(false, tailSpec.constraints.size(), String.format("NULL in %dms", ms), provDesc);
        }
        double[] gf = tsc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath p = model.forward(tsc, gf);
        double viol = JumpConstraintCompiler.compile(tailSpec).maxViolation(gf, p);
        double objX = p.getPos(tailSpec.objective.tick, tailSpec.objective.axis);
        boolean feas = viol <= 0.0;
        return new TailResult(feas, tailSpec.constraints.size(),
                String.format("%s viol=%.3e objX=%.5f in %dms", feas ? "FEASIBLE" : "infeasible", viol, objX, ms), provDesc);
    }

    private void bandSweep(ExactJumpModel model, JumpSpec full, JumpPhysicsInputs sc,
                           ForwardPath dp, double[] dgf, int a, int n) {
        double vx0 = dp.velX[a];
        double vz0 = dp.velZ[a];
        Vec3dCore pos = new Vec3dCore(dp.posX[a], dp.posY[a], dp.posZ[a]);
        double[] vzs = {vz0 - 0.06, vz0 - 0.03, vz0, vz0 + 0.03, vz0 + 0.06};
        double[] vxs = {vx0 - 0.06, vx0 - 0.03, vx0, vx0 + 0.03, vx0 + 0.06};
        System.out.printf("  proven seam vel=(%.5f,%.5f); F=feasible .=infeasible%n", vx0, vz0);
        System.out.print("            ");
        for (double vx : vxs) System.out.printf("vx=%+.3f  ", vx);
        System.out.println();
        List<JumpConstraint> cons = sliceConstraints(full, a, n);
        Objective obj = new Objective(full.objective.axis, full.objective.sense, n - a);
        for (double vz : vzs) {
            System.out.printf("  vz=%+.3f :  ", vz);
            for (double vx : vxs) {
                JumpPhysicsInputs win = sliceScenario(sc, a, n, pos, new Vec3dCore(vx, dp.velY[a], vz), (float) dgf[a - 1]);
                JumpSpec s = new JumpSpec(win, cons, obj);
                AtomicBoolean cancel = new AtomicBoolean(false);
                double[] yaws = BoundPrunedRecovery.solve(model, s, 0.0, cancel, BAND_BUDGET, -1.0e18);
                boolean feas = false;
                if (yaws != null) {
                    double[] gf = win.toGameFacings(Angles.wrapAll(yaws));
                    feas = JumpConstraintCompiler.compile(s).maxViolation(gf, model.forward(win, gf)) <= 0.0;
                }
                System.out.print(feas ? "   F      " : "   .      ");
            }
            System.out.println();
        }
    }

    private static int[] jumpBoundaries(JumpPhysicsInputs sc) {
        int n = sc.numTicks;
        List<Integer> bl = new ArrayList<>();
        bl.add(0);
        for (int t = 1; t < n; t++) {
            boolean g = !Double.isNaN(sc.slipAt(t)), gp = !Double.isNaN(sc.slipAt(t - 1));
            if (g && !gp) bl.add(t);
        }
        if (bl.get(bl.size() - 1) != n) bl.add(n);
        List<Integer> m = new ArrayList<>();
        m.add(bl.get(0));
        for (int k = 1; k < bl.size(); k++) {
            if (bl.get(k) - m.get(m.size() - 1) < 2 && k < bl.size() - 1) continue;
            m.add(bl.get(k));
        }
        int[] o = new int[m.size()];
        for (int k = 0; k < o.length; k++) o[k] = m.get(k);
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

    private static List<JumpConstraint> sliceConstraints(JumpSpec full, int a, int c) {
        List<JumpConstraint> out = new ArrayList<>();
        for (JumpConstraint jc : full.constraints) {
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
