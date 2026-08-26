package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.RelaxationRecovery;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class RelaxDiagScreen {

    private static final String[] TARGETS = {
            "hpk/d10/j335_1bmhh_Single_Fencegat_Butterfly_Neo",
            "hpk/d10/j717_Panewall_Momentum_Single_Block_Butterfly_Neo",
            "hpk/d11/j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo",
            "hpk/d11/j828-1bm_5.3125-1.5"
    };

    @Test
    public void diagnose() throws Exception {
        org.junit.Assume.assumeTrue("set PKC_SCREENS=1 to run", System.getenv("PKC_SCREENS") != null);
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        RelaxationRecovery.DEBUG = true;
        de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve.DEBUG = true;
        java.io.PrintStream orig = System.out;
        try {
            for (String rel : TARGETS) {
                URL url = getClass().getResource("/captures/" + rel + ".json");
                File f = new File(url.toURI());
                SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
                JumpSpec spec = buildSpec(file, exact);
                JumpPhysicsInputs sc = spec.asScenario();
                out.printf("%n=== %s ===  bound=%.6f%n", rel, ClosedFormSolve.dualBound(spec));
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                System.setOut(new java.io.PrintStream(buf, true, "UTF-8"));
                double[] rx = RelaxationRecovery.solve(exact, spec, 0.0, new AtomicBoolean(false));
                System.setOut(orig);
                out.print(new String(buf.toByteArray(), "UTF-8"));
                if (rx != null) {
                    double[] gf = sc.toGameFacings(Angles.wrapAll(rx));
                    out.printf("  -> SOLVED obj=%.6f%n", exact.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis));
                } else {
                    out.println("  -> failed");
                    compareToRecorded(out, file, exact, spec, sc);
                    debugReplayCheck(out, file, exact, spec, sc);
                }
            }
        } finally {
            System.setOut(orig);
            RelaxationRecovery.DEBUG = false;
            de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve.DEBUG = false;
        }
        out.flush();
        String report = sw.toString();
        System.out.println(report);
        File dst = new File("build/reports/relax-diag.txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), report.getBytes(StandardCharsets.UTF_8));
    }

    private static void compareToRecorded(PrintWriter out, SaveFile file, ExactJumpModel exact,
                                          JumpSpec spec, JumpPhysicsInputs sc) {
        double[] stalled = de.legoshi.parkourcalc.core.anglesolver.solver.RelaxationRecovery.debugLastStalled;
        if (stalled == null) {
            out.println("  no stalled point");
            return;
        }
        SaveFile.Result res = file.angleSolver.result;
        if (res == null || res.yaws == null || res.yaws.isEmpty()) {
            out.println("  no recorded yaws");
            return;
        }
        int n = sc.numTicks;
        double[] recorded = new double[n];
        java.util.Arrays.fill(recorded, Double.NaN);
        int base = file.angleSolver.startTick + 1;
        for (SaveFile.Yaw y : res.yaws) {
            int idx = y.tick - base;
            if (idx >= 0 && idx < n) recorded[idx] = y.yaw;
        }
        for (int t = 0; t < n; t++) {
            if (Double.isNaN(recorded[t])) {
                out.println("  recorded yaws incomplete");
                return;
            }
        }
        double[] gfR = recorded;
        de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath pr = exact.forward(sc, gfR);
        double violR = de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.compile(spec)
                .maxViolation(gfR, pr);
        double[] gfS = sc.toGameFacings(Angles.wrapAll(stalled.clone()));
        de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath ps = exact.forward(sc, gfS);
        double worst = Double.NEGATIVE_INFINITY;
        String worstName = "";
        for (de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint c : spec.constraints) {
            double vS = de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.slack(c, gfS, ps);
            double vR = de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.slack(c, gfR, pr);
            double eS = de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.evaluate(c, gfS, ps);
            double eR = de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.evaluate(c, gfR, pr);
            if (vS > 1.0e-9 || vR > 1.0e-9 || Math.min(Math.abs(eS), Math.abs(eR)) < 0.02) {
                out.printf("    cons %-14s %s t=%2d  stalled e=%+.6e v=%.3e   recorded e=%+.6e v=%.3e%n",
                        c.name, c.cmp + "/" + c.mode, c.t1, eS, vS, eR, vR);
            }
            if (vS > worst) {
                worst = vS;
                worstName = c.name + "@" + c.t1 + "/" + c.mode;
            }
        }
        double maxDx = 0.0;
        double maxDz = 0.0;
        double maxYawD = 0.0;
        for (int k = 0; k <= n; k++) {
            maxDx = Math.max(maxDx, Math.abs(pr.posX[k] - ps.posX[k]));
            maxDz = Math.max(maxDz, Math.abs(pr.posZ[k] - ps.posZ[k]));
        }
        for (int t = 0; t < n; t++) {
            maxYawD = Math.max(maxYawD, Math.abs(Angles.wrapDelta(gfR[t] - gfS[t])));
        }
        out.printf("  recordedViol=%.3e recordedObj=%.6f stalledWorst=%s (%.3e) pathDelta dx=%.4f dz=%.4f maxYawDelta=%.2f%n",
                violR, pr.getPos(spec.objective.tick, spec.objective.axis), worstName, worst, maxDx, maxDz, maxYawD);
        for (int t = 0; t < n; t++) {
            out.printf("    yaw t=%2d stalled=%9.4f recorded=%9.4f delta=%+8.4f%n",
                    t, gfS[t], gfR[t], Angles.wrapDelta(gfR[t] - gfS[t]));
        }
    }

    private static void debugReplayCheck(PrintWriter out, SaveFile file, ExactJumpModel exact,
                                         JumpSpec spec, JumpPhysicsInputs sc) {
        java.util.List<SaveFile.DebugTick> dbg = file.debug;
        if (dbg == null || dbg.isEmpty()) {
            out.println("  no debug block");
            return;
        }
        int start = file.angleSolver.startTick;
        int n = sc.numTicks;
        if (start + n >= dbg.size()) {
            out.println("  debug too short");
            return;
        }
        double[] yaws = new double[n];
        for (int i = 0; i < n; i++) yaws[i] = dbg.get(start + 1 + i).yaw;
        de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath p = exact.forward(sc, yaws);
        double maxD = 0.0;
        for (int i = 0; i <= n; i++) {
            SaveFile.DebugTick d = dbg.get(start + i);
            maxD = Math.max(maxD, Math.max(Math.abs(p.posX[i] - d.pos[0]), Math.abs(p.posZ[i] - d.pos[2])));
        }
        double viol = de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.compile(spec)
                .maxViolation(yaws, p);
        out.printf("  debugReplay maxPosDelta=%.2e specViolOfDebugPath=%.3e debugObj=%.6f%n",
                maxD, viol, p.getPos(spec.objective.tick, spec.objective.axis));
        for (int i = 0; i < n; i++) {
            SaveFile.DebugTick d = dbg.get(start + 1 + i);
            double delta = Math.max(Math.abs(p.posX[i + 1] - d.pos[0]), Math.abs(p.posZ[i + 1] - d.pos[2]));
            String mark = delta > 1.0e-9 ? " <== drift" : "";
            out.printf("    t=%2d slip=%s dbgGround=%-5s scSprint=%-5s dbgSprint=%-5s scF=%.2f dbgF=%s scS=%.2f dbgS=%s jump=%-5s delta=%.2e%s%n",
                    i, Double.isNaN(sc.slipAt(i)) ? "air" : String.valueOf(sc.slipAt(i)), d.onGround,
                    sc.sprintAt(i), d.sprinting, sc.forwardAt(i),
                    d.moveForward == null ? "?" : String.format("%.2f", d.moveForward),
                    sc.strafeAt(i) ? 0.98 * sc.strafeSign : sc.strafeInputAt(i),
                    d.moveStrafe == null ? "?" : String.format("%.2f", d.moveStrafe),
                    sc.jumpAt(i), delta, mark);
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
