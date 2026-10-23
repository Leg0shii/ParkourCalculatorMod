package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.DiskSocpKernel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class KernelDiagProbe {

    @Test
    public void run() throws Exception {
        String capPath = System.getenv("PKC_KDIAG_CAPTURE");
        Assume.assumeTrue("set PKC_KDIAG_CAPTURE to a capture path", capPath != null && !capPath.isEmpty());

        String raw;
        File direct = new File(capPath);
        if (direct.isFile()) {
            raw = new String(Files.readAllBytes(direct.toPath()), StandardCharsets.UTF_8);
        } else {
            raw = Fixtures.rawPool(capPath);
        }
        SaveFile file = SaveIO.parseSafe(raw);
        if (file == null) throw new IllegalStateException(capPath + ": failed to parse");

        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        if (spec == null) throw new IllegalStateException(capPath + ": no spec");

        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JumpLinearModel lin = new JumpLinearModel(sc);
        double[] cx = new double[n];
        double[] cz = new double[n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivialFlag = {false};
        double relax = envD("PKC_KDIAG_RELAX", 0.0);
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, -relax, trivialFlag);
        System.out.printf("KDIAG %s n=%d walls=%d trivial=%b relax=%.3g%n",
                capPath, n, walls.size(), trivialFlag[0], relax);

        StartBox box = sc.startBox;
        CostateDualSolver.FreeP0 freeP0 = null;
        if (box != null && box.startFree()) {
            boolean max = spec.objective.sense == Objective.Sense.MAX;
            double devX = spec.objective.axis == JumpPhysicsInputs.Axis.X ? (max ? 1.0 : -1.0) : 0.0;
            double devZ = spec.objective.axis == JumpPhysicsInputs.Axis.X ? 0.0 : (max ? 1.0 : -1.0);
            freeP0 = new CostateDualSolver.FreeP0(box.pxLo - box.px, box.pxHi - box.px,
                    box.pzLo - box.pz, box.pzHi - box.pz, devX, devZ, 5.0e-4);
        }

        if ("1".equals(System.getenv("PKC_KDIAG_DEBUG"))) DiskSocpKernel.DEBUG = true;
        DiskSocpKernel.Outcome base = DiskSocpKernel.solveChords(n, cx, cz, lin.mMagAll(), walls, freeP0, null, null);
        report("BASE", base, lin, n);

        String chordDeg = System.getenv("PKC_KDIAG_CHORD");
        if (chordDeg != null && !chordDeg.isEmpty() && base.result != null) {
            double delta = Math.toRadians(Double.parseDouble(chordDeg));
            List<DiskSocpKernel.ChordRow> chords = new ArrayList<>();
            for (int t = 0; t < n; t++) {
                double mm = lin.mMag(t);
                if (mm <= 0.0) continue;
                double slack = mm - Math.hypot(base.result.ux[t], base.result.uz[t]);
                if (slack <= 1.0e-4 * Math.max(mm, 0.026)) continue;
                double mu = Math.hypot(base.result.ux[t], base.result.uz[t]) < 1.0e-12
                        ? lin.baseArg(t) : Math.atan2(base.result.uz[t], base.result.ux[t]);
                chords.add(new DiskSocpKernel.ChordRow(t, Math.cos(mu), Math.sin(mu),
                        mm * Math.cos(delta), "chord@" + t));
            }
            System.out.printf("KDIAG chords=%d deltaDeg=%s%n", chords.size(), chordDeg);
            DiskSocpKernel.Outcome ch = DiskSocpKernel.solveChords(n, cx, cz, lin.mMagAll(), walls, freeP0, chords, null);
            report("CHORD", ch, lin, n);
        }
    }

    private static void report(String tag, DiskSocpKernel.Outcome oc, JumpLinearModel lin, int n) {
        if (oc.result == null) {
            System.out.printf("KDIAG %s result=null failCode=%d failIter=%d failMu=%.3e%n",
                    tag, oc.failCode, oc.failIter, oc.failMu);
            return;
        }
        DiskSocpKernel.Result r = oc.result;
        int interior = 0;
        double maxSlack = 0.0;
        for (int t = 0; t < n; t++) {
            double mm = lin.mMag(t);
            if (mm <= 0.0) continue;
            double slack = mm - Math.hypot(r.ux[t], r.uz[t]);
            if (slack > 1.0e-4 * Math.max(mm, 0.026)) interior++;
            maxSlack = Math.max(maxSlack, slack);
        }
        System.out.printf("KDIAG %s converged=%b failCode=%d iters=%d gap=%.3e value=%.9f interior=%d maxSlack=%.3e%n",
                tag, r.converged, oc.failCode, r.iters, r.gap, r.value, interior, maxSlack);
        if ("1".equals(System.getenv("PKC_KDIAG_TICKS"))) {
            for (int t = 0; t < n; t++) {
                double mm = lin.mMag(t);
                if (mm <= 0.0) continue;
                double uNorm = Math.hypot(r.ux[t], r.uz[t]);
                double gNorm = Math.hypot(r.gx[t], r.gz[t]);
                String ref = "-";
                if (r.uxRef != null) {
                    ref = String.format("|uRef|=%.4e refDir=%.3f",
                            Math.hypot(r.uxRef[t], r.uzRef[t]),
                            Math.toDegrees(Math.atan2(r.uzRef[t], r.uxRef[t])));
                }
                System.out.printf("  tick=%d mm=%.4f |u|=%.6e slack=%.3e |g|=%.3e dir=%.3f %s%n",
                        t, mm, uNorm, mm - uNorm, gNorm, Math.toDegrees(Math.atan2(r.uz[t], r.ux[t])), ref);
            }
        }
    }

    private static double envD(String key, double dflt) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? dflt : Double.parseDouble(v);
    }
}
