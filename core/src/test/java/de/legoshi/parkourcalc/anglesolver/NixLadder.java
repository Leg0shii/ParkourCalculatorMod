package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.HomotopyCloser;
import de.legoshi.parkourcalc.core.anglesolver.solver.IlsPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class NixLadder {

    private ExactJumpModel model;
    private JumpPhysicsInputs full;

    @Test
    public void rung() throws Exception {
        String path = System.getenv("PKC_LD_FILE");
        org.junit.Assume.assumeTrue("set PKC_LD_FILE", path != null && !path.isEmpty());
        double wall = Double.parseDouble(System.getenv().getOrDefault("PKC_LD_WALL", "1.3875"));
        double floor = Double.parseDouble(System.getenv().getOrDefault("PKC_LD_FLOOR", "8.7"));
        String warmDump = System.getenv("PKC_LD_WARMDUMP");
        long budgetS = Long.parseLong(System.getenv().getOrDefault("PKC_LD_BUDGET_S", "900"));
        long t0 = System.nanoTime();
        long deadline = t0 + budgetS * 1_000_000_000L;

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
        int n = full.numTicks;
        String sx = System.getenv("PKC_LD_STARTX");
        String sz = System.getenv("PKC_LD_STARTZ");
        if (sx != null || sz != null) {
            double px = sx != null ? Double.parseDouble(sx) : full.startPos.x;
            double pz = sz != null ? Double.parseDouble(sz) : full.startPos.z;
            full.startPos = new de.legoshi.parkourcalc.core.sim.Vec3dCore(px, full.startPos.y, pz);
        }
        full.startBox = StartBox.pinned(full.startPos.x, full.startPos.z, full.initialVelocity.x, full.initialVelocity.z);

        String jumpVar = System.getenv().getOrDefault("PKC_LD_JUMPVAR", "50,49");
        List<Integer> airborneNow = new ArrayList<>();
        if (!jumpVar.equals("none")) {
            boolean[] jp = full.jumpPerTick.clone();
            double[] slp = full.slipPerTick.clone();
            for (String pair : jumpVar.split(";")) {
                int from = Integer.parseInt(pair.split(",")[0]);
                int to = Integer.parseInt(pair.split(",")[1]);
                jp[from] = false;
                jp[to] = true;
                for (int t = to + 1; t < n && t <= from; t++) {
                    if (!Double.isNaN(slp[t])) airborneNow.add(t);
                    slp[t] = Double.NaN;
                }
                airborneNow.add(to);
            }
            full.jumpPerTick = jp;
            full.slipPerTick = slp;
        }

        int objTick = spec.objective.tick;
        boolean dropBox = "1".equals(System.getenv("PKC_LD_DROPBOX"));
        List<JumpConstraint> cons = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) {
            if (dropBox && c.t1 < 50 && c.t2 == null && airborneNow.contains(c.t1)) continue;
            if (c.mode == JumpConstraint.Mode.X && c.t1 >= objTick - 2 && c.t2 == null
                    && c.cmp == JumpConstraint.Cmp.GE && c.rhs < floor && c.rhs > floor - 0.02) {
                cons.add(new JumpConstraint(c.mode, c.t1, c.t2, c.op, c.cmp, floor, c.name));
            } else {
                cons.add(c);
            }
        }
        for (int t = 1; t <= 49 && t < n; t++) {
            cons.add(new JumpConstraint(JumpConstraint.Mode.Z, t, null, JumpConstraint.Op.PLUS,
                    JumpConstraint.Cmp.GE, wall, "pocketWall"));
        }
        JumpSpec tight = new JumpSpec(full, cons, spec.objective);
        System.out.printf(Locale.ROOT, "=== NixLadder %s wall=%.4f floor=%.4f n=%d cons=%d jumpVar=%s start=(%.4f,%.4f) ===%n",
                new File(path).getName(), wall, floor, n, cons.size(), jumpVar, full.startPos.x, full.startPos.z);

        double[] warm = new double[n];
        for (int k = 0; k < Math.min(24, n); k++) warm[k] = file.debug.get(k + 1).yaw;
        if (warmDump != null && !warmDump.isEmpty()) {
            for (String line : Files.readAllLines(new File(warmDump).toPath())) {
                if (line.startsWith("TASYAW")) {
                    String[] p = line.split(" ");
                    int k = Integer.parseInt(p[1]);
                    if (k >= 0 && k < n) warm[k] = Double.parseDouble(p[2]);
                }
            }
        } else {
            for (int k = 24; k < n; k++) warm[k] = file.debug.get(k + 1).yaw;
        }
        double wv = HomotopyCloser.slack(model, tight, warm);
        System.out.printf(Locale.ROOT, "warm viol=%.6e%n", wv);

        AtomicBoolean cancel = new AtomicBoolean(false);
        double[] y = HomotopyCloser.close(model, tight, warm, Math.max(2.0 * wv, 1.0e-3), deadline, cancel);
        if (y != null && emit("homotopy", tight, y, floor, t0)) return;
        System.out.printf(Locale.ROOT, "homotopy missed (%.1fs)%n", sec(t0));

        List<JumpConstraint> noPad = new ArrayList<>();
        for (JumpConstraint c : cons) {
            if (c.mode == JumpConstraint.Mode.X && c.t1 >= objTick - 2 && c.t2 == null && c.cmp == JumpConstraint.Cmp.GE) continue;
            noPad.add(c);
        }
        JumpSpec ceiling = new JumpSpec(full, noPad, spec.objective);
        double[] cma = SolveCore.optimize(model, ceiling, new SolveCore.Budget(192, 100000, 16, BucketAscentPolish.THOROUGH),
                20.0, 0.0, cancel, Angles.wrapAll(warm.clone()));
        double bestObj = Double.NEGATIVE_INFINITY;
        double[] best = null;
        if (cma != null) {
            double v = HomotopyCloser.slack(model, ceiling, cma);
            double o = objX(ceiling, cma);
            System.out.printf(Locale.ROOT, "ceiling SolveCore: objX=%.7f viol=%.3e (%.1fs)%n", o, Math.max(0, v), sec(t0));
            if (v <= 0.0) { bestObj = o; best = cma; }
        }
        if (best != null) {
            double[] ils = IlsPolish.polish(model, ceiling, Angles.wrapAll(best),
                    Math.min(deadline, System.nanoTime() + 120_000_000_000L), 300, false, cancel, null);
            double o = objX(ceiling, ils);
            double v = HomotopyCloser.slack(model, ceiling, ils);
            System.out.printf(Locale.ROOT, "ceiling +ILS: objX=%.7f viol=%.3e (%.1fs)%n", o, Math.max(0, v), sec(t0));
            if (v <= 0.0 && o > bestObj) { bestObj = o; best = ils; }
            if (best != null && bestObj >= floor && emit("ceiling", tight, best, floor, t0)) return;
        }
        System.out.printf(Locale.ROOT, "RUNG FAILED wall=%.4f: best ceiling objX=%.7f (need %.4f) (%.1fs)%n",
                wall, bestObj, floor, sec(t0));
    }

    private boolean emit(String via, JumpSpec tight, double[] yRaw, double floor, long t0) {
        double[] y = Angles.wrapAll(yRaw.clone());
        double[] gf = full.toGameFacings(y);
        ForwardPath p = model.forward(full, gf);
        double viol = JumpConstraintCompiler.compile(tight).maxViolation(gf, p);
        double obj = p.getPos(tight.objective.tick, tight.objective.axis);
        if (viol > 0.0 || obj < floor) return false;
        double zmin = Double.POSITIVE_INFINITY;
        for (int k = 0; k <= full.numTicks; k++) zmin = Math.min(zmin, p.posZ[k]);
        System.out.printf(Locale.ROOT, "%n*** RUNG LANDED via %s *** viol=%.3e objX=%.9f zMin=%.9f (%.1fs)%n",
                via, viol, obj, zmin, sec(t0));
        for (int k = 0; k < y.length; k++) System.out.printf(Locale.ROOT, "TASYAW %d %.9f%n", k, y[k]);
        for (int k = 0; k <= full.numTicks; k++) System.out.printf(Locale.ROOT, "TASPOS %d %.9f %.9f%n", k, p.posX[k], p.posZ[k]);
        return true;
    }

    private double objX(JumpSpec spec, double[] yawsAbs) {
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs.clone()));
        return model.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
    }

    private static double sec(long t0) {
        return (System.nanoTime() - t0) / 1.0e9;
    }
}
