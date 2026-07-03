package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.RelaxationRecovery;
import de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class HpkDualRecoveryScreen {

    @Test
    public void screen() throws Exception {
        org.junit.Assume.assumeTrue("set PKC_SCREENS=1 to run", System.getenv("PKC_SCREENS") != null);
        List<File> files = new ArrayList<>();
        collect(resolve("/captures/hpk"), files);
        File loopmm = new File(resolve("/captures"), "loopmm-3jump-lands.json");
        if (loopmm.isFile()) files.add(loopmm);

        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.printf("%-52s %3s %2s %3s %14s %14s %9s %10s %8s %10s %8s %12s %8s %9s %9s%n",
                "capture", "n", "j", "m", "recordedObj", "dualBound", "cfSolve", "cfObj", "cfMs",
                "slpObj", "slpMs", "rxObj", "rxMs", "contViol", "exViol");

        for (File f : files) {
            String stem = f.getName().substring(0, f.getName().length() - ".json".length());
            try {
                screenOne(stem, f, out);
            } catch (Throwable t) {
                out.printf("%-52s EXC %s%n", stem, t);
            }
        }

        out.flush();
        String report = sw.toString();
        System.out.println(report);
        File dst = new File("build/reports/hpk-screen.txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), report.getBytes(StandardCharsets.UTF_8));
    }

    private void screenOne(String stem, File f, PrintWriter out) throws Exception {
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        if (file == null || file.angleSolver == null || file.angleSolver.seed == null
                || file.rows == null || file.rows.isEmpty()) {
            out.printf("%-52s (skipped: no angleSolver/seed/rows)%n", stem);
            return;
        }
        ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
        JumpSpec spec = buildSpec(file, exact);
        if (spec == null) {
            out.printf("%-52s (skipped: null spec)%n", stem);
            return;
        }
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        int n = sc.numTicks;
        int jumps = 0;
        for (int t = 0; t < n; t++) if (sc.jumpAt(t) && !Double.isNaN(sc.slipAt(t))) jumps++;

        double recorded = file.angleSolver.result != null && file.angleSolver.result.hasObjective
                ? file.angleSolver.result.objectiveValue : Double.NaN;

        double bound = ClosedFormSolve.dualBound(spec);

        AtomicBoolean cancel = new AtomicBoolean(false);
        long t0 = System.nanoTime();
        double[] cf = ClosedFormSolve.optimize(exact, spec, 0.0, cancel);
        double cfMs = (System.nanoTime() - t0) / 1e6;
        double cfObj = Double.NaN;
        if (cf != null) {
            double[] gf = sc.toGameFacings(Angles.wrapAll(cf));
            cfObj = exact.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
        }

        t0 = System.nanoTime();
        double[] slp = SlpSolve.optimize(exact, spec, 0.0, cancel);
        double slpMs = (System.nanoTime() - t0) / 1e6;
        double slpObj = Double.NaN;
        if (slp != null) {
            double[] gf = sc.toGameFacings(Angles.wrapAll(slp));
            slpObj = exact.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
        }

        t0 = System.nanoTime();
        double[] alt = null;
        if (cf == null && slp == null) {
            for (Objective altObj : alternateObjectives(spec.objective)) {
                double[] altSeed = ClosedFormSolve.optimize(exact,
                        new JumpSpec(sc, spec.constraints, altObj), 0.0, cancel);
                if (altSeed == null) continue;
                alt = SlpSolve.optimize(exact, spec, 0.0, cancel, altSeed);
                if (alt != null) break;
            }
        }
        double altMs = (System.nanoTime() - t0) / 1e6;
        double altObjVal = Double.NaN;
        if (alt != null) {
            double[] gf = sc.toGameFacings(Angles.wrapAll(alt));
            altObjVal = exact.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
        }

        t0 = System.nanoTime();
        double[] rx = RelaxationRecovery.solve(exact, spec, 0.0, cancel);
        double rxMs = (System.nanoTime() - t0) / 1e6;
        double rxObj = Double.NaN;
        if (rx != null) {
            double[] gf = sc.toGameFacings(Angles.wrapAll(rx));
            rxObj = exact.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
        }

        double contViol = Double.NaN;
        double exViol = Double.NaN;
        String detail = "";
        if (!JumpLinearModel.hasFacingWall(spec.constraints)) {
            JumpLinearModel lin = new JumpLinearModel(sc);
            double[] cx = new double[n];
            double[] cz = new double[n];
            lin.objectiveVectors(spec.objective, cx, cz);
            boolean[] trivial = {false};
            List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivial);
            if (!trivial[0]) {
                CostateDualSolver.Result r = new CostateDualSolver(n, cx, cz, lin.mMagAll(), walls).solve(0.0, null);
                if (r != null) {
                    boolean max = spec.objective.sense == Objective.Sense.MAX;
                    boolean axisX = spec.objective.axis == JumpPhysicsInputs.Axis.X;
                    double[] ux = new double[n];
                    double[] uz = new double[n];
                    double[] yaws = new double[n];
                    double[] mMag = lin.mMagAll();
                    for (int t = 0; t < n; t++) {
                        double gx = r.gx[t], gz = r.gz[t];
                        if (gx * gx + gz * gz < 1.0e-18) {
                            gx = axisX ? (max ? 1.0 : -1.0) : 0.0;
                            gz = axisX ? 0.0 : (max ? 1.0 : -1.0);
                        }
                        double inv = mMag[t] / Math.sqrt(gx * gx + gz * gz);
                        ux[t] = gx * inv;
                        uz[t] = gz * inv;
                        yaws[t] = lin.recoverYawDeg(t, gx, gz);
                    }
                    contViol = 0.0;
                    for (JumpLinearModel.Wall w : walls) {
                        double au = 0.0;
                        for (int s = 0; s < n; s++) au += w.coef[s] * (w.axis == 0 ? ux[s] : uz[s]);
                        double g = au - w.bPrime;
                        double v = w.eq ? Math.abs(g) : g;
                        if (v > contViol) contViol = v;
                    }
                    double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
                    ForwardPath full = exact.forward(sc, gf);
                    exViol = compiled.maxViolation(gf, full);
                    ForwardPath sineOnly = new ExactJumpModel(0.0, true,
                            !exact.perAxisInertia()).forward(sc, gf);
                    double maxIner = 0.0, maxSine = 0.0;
                    for (int k = 0; k <= n; k++) {
                        double lx = lin.constPos(k, 0), lz = lin.constPos(k, 1);
                        for (int s = 0; s < n; s++) {
                            lx += lin.coef(s, k) * ux[s];
                            lz += lin.coef(s, k) * uz[s];
                        }
                        maxSine = Math.max(maxSine, Math.max(Math.abs(sineOnly.posX[k] - lx),
                                Math.abs(sineOnly.posZ[k] - lz)));
                        maxIner = Math.max(maxIner, Math.max(Math.abs(full.posX[k] - sineOnly.posX[k]),
                                Math.abs(full.posZ[k] - sineOnly.posZ[k])));
                    }
                    detail = String.format("  sine=%.1e iner=%.1e", maxSine, maxIner);
                }
            }
        }

        out.printf("%-52s %3d %2d %3d %14.6f %14s %9s %10s %8.1f %10s %8.1f %12s %8.1f %9s %9s%s%n",
                stem, n, jumps, spec.constraints.size(), recorded,
                Double.isNaN(bound) ? "NaN" : String.format("%.6f", bound),
                cf != null ? "OK" : "null",
                Double.isNaN(cfObj) ? "-" : String.format("%.4f", cfObj), cfMs,
                Double.isNaN(slpObj) ? "-" : String.format("%.4f", slpObj)
                        + (Double.isNaN(altObjVal) ? "" : String.format("|A%.4f", altObjVal)), slpMs + altMs,
                Double.isNaN(rxObj) ? "-" : String.format("%.6f", rxObj), rxMs,
                Double.isNaN(contViol) ? "-" : String.format("%.2e", contViol),
                Double.isNaN(exViol) ? "-" : String.format("%.2e", exViol),
                detail);
    }

    private static java.util.List<Objective> alternateObjectives(Objective o) {
        java.util.List<Objective> out = new ArrayList<>();
        JumpPhysicsInputs.Axis[] axisOrder = (o.axis == JumpPhysicsInputs.Axis.X)
                ? new JumpPhysicsInputs.Axis[]{JumpPhysicsInputs.Axis.X, JumpPhysicsInputs.Axis.Z}
                : new JumpPhysicsInputs.Axis[]{JumpPhysicsInputs.Axis.Z, JumpPhysicsInputs.Axis.X};
        for (JumpPhysicsInputs.Axis ax : axisOrder) {
            for (Objective.Sense se : new Objective.Sense[]{Objective.Sense.MAX, Objective.Sense.MIN}) {
                if (ax == o.axis && se == o.sense) continue;
                out.add(new Objective(ax, se, o.tick));
            }
        }
        return out;
    }

    private static File resolve(String path) throws Exception {
        URL url = HpkDualRecoveryScreen.class.getResource(path);
        if (url == null) throw new IllegalStateException("missing " + path);
        return new File(url.toURI());
    }

    private static void collect(File dir, List<File> out) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        Arrays.sort(entries);
        for (File e : entries) {
            if (e.isDirectory()) collect(e, out);
            else if (e.getName().endsWith(".json")) out.add(e);
        }
    }

    private static JumpSpec buildSpec(SaveFile file, ExactJumpModel model) {
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        return engine.debugBuildSpec();
    }
}
