package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class LoopmmReachScreen {

    private static final double PAD_EDGE = -279.3;
    private static final long TIMEOUT_MS = 300_000L;

    @Test
    public void boundPrunedBnb() throws Exception {
        org.junit.Assume.assumeTrue("set PKC_SCREENS=1 to run", System.getenv("PKC_SCREENS") != null);
        URL url = getClass().getResource("/captures/loopmm-3jump-lands.json");
        File f = new File(url.toURI());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, exact);
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec loose = engine.debugBuildSpec();
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs sc = loose.asScenario();
        java.util.concurrent.atomic.AtomicBoolean cancel = new java.util.concurrent.atomic.AtomicBoolean(false);
        de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery.DEBUG = true;

        long t0 = System.nanoTime();
        double[] bnb = de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery.solve(
                exact, loose, 0.0, cancel, 60_000_000_000L, PAD_EDGE);
        System.out.printf("loose bnb: %d ms%n", (System.nanoTime() - t0) / 1_000_000L);
        reachReport(exact, sc, loose, "bnb-loose", bnb);

        java.util.List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint> tightened =
                new java.util.ArrayList<>(loose.constraints);
        tightened.add(new de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint(
                de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Mode.Z, loose.objective.tick, null,
                de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Op.PLUS,
                de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Cmp.GE, PAD_EDGE, "padZ"));
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec tight =
                new de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec(sc, tightened, loose.objective);
        t0 = System.nanoTime();
        double[] bnbT = de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery.solve(
                exact, tight, 0.0, cancel, 60_000_000_000L);
        System.out.printf("tight bnb: %d ms%n", (System.nanoTime() - t0) / 1_000_000L);
        reachReport(exact, sc, tight, "bnb-tight", bnbT);
        de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery.DEBUG = false;
    }

    private static void reachReport(ExactJumpModel exact,
                                    de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs sc,
                                    de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec spec,
                                    String label, double[] yaws) {
        if (yaws == null) {
            System.out.printf("  %-10s null%n", label);
            return;
        }
        double[] gf = sc.toGameFacings(de.legoshi.parkourcalc.core.anglesolver.solver.Angles.wrapAll(yaws));
        de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath p = exact.forward(sc, gf);
        double viol = de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.compile(spec)
                .maxViolation(gf, p);
        double z = p.getPos(spec.objective.tick, spec.objective.axis);
        System.out.printf("  %-10s viol=%.3e obj=%.6f padMargin=%+.6f lands=%s%n",
                label, viol, z, z - PAD_EDGE, viol <= 0.0 && z >= PAD_EDGE);
    }

    @Test
    public void patternBranchedClosedForm() throws Exception {
        org.junit.Assume.assumeTrue("set PKC_SCREENS=1 to run", System.getenv("PKC_SCREENS") != null);
        URL url = getClass().getResource("/captures/loopmm-3jump-lands.json");
        File f = new File(url.toURI());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, exact);
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec spec = engine.debugBuildSpec();
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs sc = spec.asScenario();
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.Compiled compiled =
                de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.compile(spec);
        int n = sc.numTicks;
        double thr = exact.inertiaThreshold();
        double[] margins = {0.0, 3.0e-4, 1.2e-3, 5.0e-3};
        java.util.concurrent.atomic.AtomicBoolean cancel = new java.util.concurrent.atomic.AtomicBoolean(false);

        double bestObj = Double.NEGATIVE_INFINITY;
        int bestK = -1;
        for (int k = 0; k <= n; k++) {
            boolean[] zeroX = new boolean[n];
            boolean[] zeroZ = new boolean[n];
            for (int t = k; t < n; t++) zeroX[t] = true;
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel lin =
                    new de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel(sc, zeroX, zeroZ);
            double[] cx = new double[n];
            double[] cz = new double[n];
            lin.objectiveVectors(spec.objective, cx, cz);
            boolean[] trivial = {false};
            java.util.List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel.Wall> base =
                    lin.compileWalls(spec.constraints, 0.0, trivial);
            if (trivial[0]) continue;
            base.addAll(lin.velocityWalls(thr));
            de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver.Result r0 =
                    new de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver(n, cx, cz, lin.mMagAll(), base)
                            .solve(0.0, null);
            if (r0 == null) continue;
            double bound = r0.value + lin.constPos(spec.objective.tick,
                    spec.objective.axis == de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs.Axis.X ? 0 : 1);
            double[] warm = r0.lambda;
            double bestExact = Double.NaN;
            for (double margin : margins) {
                boolean[] triv = {false};
                java.util.List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel.Wall> walls =
                        lin.compileWalls(spec.constraints, margin, triv);
                if (triv[0]) break;
                walls.addAll(lin.velocityWalls(thr));
                de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver.Result r =
                        new de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver(n, cx, cz, lin.mMagAll(), walls)
                                .solve(margin, warm);
                if (r == null) continue;
                warm = r.lambda;
                double[] yaws = new double[n];
                for (int t = 0; t < n; t++) {
                    double gx = r.gx[t];
                    double gz = r.gz[t];
                    if (gx * gx + gz * gz < 1.0e-18) {
                        gx = 0.0;
                        gz = 1.0;
                    }
                    yaws[t] = lin.recoverYawDeg(t, gx, gz);
                }
                double[] gf = sc.toGameFacings(de.legoshi.parkourcalc.core.anglesolver.solver.Angles.wrapAll(yaws));
                de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath p = exact.forward(sc, gf);
                double viol = compiled.maxViolation(gf, p);
                if (viol <= 0.0) {
                    bestExact = p.getPos(spec.objective.tick, spec.objective.axis);
                    break;
                }
                if (cancel.get()) break;
                double[] slp = de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve.optimizeBestEffort(
                        exact, spec, 0.0, cancel, yaws, 160, 220, true);
                if (slp != null) {
                    double[] gf2 = sc.toGameFacings(de.legoshi.parkourcalc.core.anglesolver.solver.Angles.wrapAll(slp));
                    de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath p2 = exact.forward(sc, gf2);
                    if (compiled.maxViolation(gf2, p2) <= 0.0) {
                        double o2 = p2.getPos(spec.objective.tick, spec.objective.axis);
                        if (Double.isNaN(bestExact) || o2 > bestExact) bestExact = o2;
                    }
                }
            }
            if (!Double.isNaN(bestExact) || k >= n - 6 || k == 0) {
                System.out.printf("k=%2d bound=%.6f exact=%s%n", k, bound,
                        Double.isNaN(bestExact) ? "-" : String.format("%.6f padMargin=%+.6f", bestExact, bestExact - PAD_EDGE));
            }
            if (!Double.isNaN(bestExact) && bestExact > bestObj) {
                bestObj = bestExact;
                bestK = k;
            }
        }
        System.out.printf("BEST k=%d obj=%.6f padMargin=%+.6f lands=%s%n",
                bestK, bestObj, bestObj - PAD_EDGE, bestObj >= PAD_EDGE);
    }

    @Test
    public void engineExhaustive() throws Exception {
        org.junit.Assume.assumeTrue("set PKC_SCREENS=1 to run", System.getenv("PKC_SCREENS") != null);
        URL url = getClass().getResource("/captures/loopmm-3jump-lands.json");
        File f = new File(url.toURI());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.CUSTOM);
        state.getSolveBudget().setIlsExhaustive(true);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, exact);

        long t0 = System.nanoTime();
        engine.solve();
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            Thread.sleep(5);
        }
        engine.poll();
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        SolveResult r = state.getResult();
        if (r == null) {
            System.out.println("no result after " + ms + " ms");
            return;
        }
        String solver = "?";
        try {
            solver = String.valueOf(r.getClass().getMethod("getSolver").invoke(r));
        } catch (Exception ignored) {
        }
        double obj = r.hasObjective() ? r.getObjectiveValue() : Double.NaN;
        System.out.printf("success=%s met=%d/%d ms=%d obj=%.6f padMargin=%+.6f%nsolver=%s%n",
                r.isSuccess(), r.getMet(), r.getTotal(), ms, obj, obj - PAD_EDGE, solver);
    }
}
