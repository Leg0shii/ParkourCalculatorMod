package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.FacingPrefold;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class ColdProbeDebugScreen {

    @Test
    public void probeHeldLine() throws Exception {
        String path = System.getenv("PKC_COLD_PROBE_FILE");
        Assume.assumeTrue("set PKC_COLD_PROBE_FILE=<capture.json> to run", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdProblem p = ColdProblem.fromSave(file);
        AtomicBoolean cancel = new AtomicBoolean(false);

        int combo = Integer.parseInt(env("PKC_COLD_PROBE_COMBO", "1"));
        int[] moveKey = new int[p.lastPressSeg + 1];
        boolean[] hold = new boolean[p.lastPressSeg + 1];
        Arrays.fill(moveKey, combo);
        Arrays.fill(hold, KeyLine.canRun(combo));
        KeyLine line = new KeyLine(p, moveKey, hold);

        double[] thetas = {0.0, 45.0, 90.0, 135.0, 180.0, -45.0, -90.0, -135.0, 66.0};
        for (double theta : thetas) {
            double cx = 0.5 * (p.rectXLo + p.rectXHi);
            double cz = 0.5 * (p.rectZLo + p.rectZHi);
            JumpSpec full = LineSpec.build(line, theta, cx, cz);
            if (full == null) {
                System.out.printf(Locale.ROOT, "theta=%.1f full spec null%n", theta);
                continue;
            }
            JumpPhysicsInputs sc = full.asScenario();
            double[] yaws = new double[sc.numTicks];
            Arrays.fill(yaws, theta);
            double[] gf = sc.toGameFacings(yaws);
            ForwardPath fp = p.model.forward(sc, gf);
            int lp = p.lastPressSeg;
            System.out.printf(Locale.ROOT,
                    "theta=%6.1f pressPos=(%.4f, %.4f) pressVel=(%.4f, %.4f) speed=%.4f%n",
                    theta, fp.posX[lp], fp.posZ[lp], fp.velX[lp], fp.velZ[lp],
                    Math.hypot(fp.velX[lp], fp.velZ[lp]));

            JumpSpec probeSpec = ColdSearch.buildSliceSpec(p, line, theta,
                    fp.posX[lp], fp.posZ[lp], fp.posX[lp], fp.posX[lp], fp.posZ[lp], fp.posZ[lp],
                    fp.velX[lp], fp.velZ[lp]);
            JumpLinearModel lin = new JumpLinearModel(probeSpec.asScenario());
            FacingPrefold pre = FacingPrefold.analyze(probeSpec.constraints, lin);
            FacingPrefold.ChainScan scan = pre == null
                    ? FacingPrefold.scannable(probeSpec.constraints, lin) : null;
            ClosedFormSolve.Result r = null;
            String err = "";
            try {
                r = ClosedFormSolve.optimizeRobustGraded(p.model, probeSpec, 0.0, cancel);
            } catch (RuntimeException ex) {
                err = ex.toString();
            }
            System.out.printf(Locale.ROOT,
                    "   probe cons=%d prefold=%s scan=%s result=%s viol=%s err=%s%n",
                    probeSpec.constraints.size(),
                    pre == null ? "null" : ("vars=" + pre.reducedVars() + " pinned=" + pre.pinnedTicks()),
                    scan == null ? "null" : "scan",
                    r == null ? "null" : (r.feasible ? "FEASIBLE" : "infeasible"),
                    r == null ? "-" : String.format(Locale.ROOT, "%.4e", r.violation), err);

            FreeStartSolve.Result js = null;
            String jerr = "";
            try {
                js = FreeStartSolve.solveJoint(p.model, full, 0.0, cancel);
                if (js == null || !js.feasible) js = FreeStartSolve.solve(p.model, full, 0.0, cancel);
            } catch (RuntimeException ex) {
                jerr = ex.toString();
            }
            System.out.printf(Locale.ROOT, "   joint=%s debug=%s err=%s%n",
                    js == null ? "null" : (js.feasible ? String.format(Locale.ROOT,
                            "FEASIBLE start=(%.4f,%.4f) f0=%.3f", js.startX, js.startZ,
                            Angles.wrap(js.yaws[0])) : "infeasible"),
                    FreeStartSolve.lastJointDebug, jerr);
        }

        JumpSpec full = LineSpec.build(line, 0.0, 0.5 * (p.rectXLo + p.rectXHi), 0.5 * (p.rectZLo + p.rectZHi));
        JumpLinearModel linFull = new JumpLinearModel(full.asScenario());
        FacingPrefold preFull = FacingPrefold.analyze(full.constraints, linFull);
        FacingPrefold.ChainScan scanFull = preFull == null
                ? FacingPrefold.scannable(full.constraints, linFull) : null;
        System.out.printf(Locale.ROOT, "full spec cons=%d prefold=%s scan=%s startBox=%s%n",
                full.constraints.size(),
                preFull == null ? "null" : ("vars=" + preFull.reducedVars()),
                scanFull == null ? "null" : "scan",
                full.asScenario().startBox == null ? "null" : full.asScenario().startBox.label());
        double bestViol = Double.POSITIVE_INFINITY;
        double bestTheta = Double.NaN;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(full);
        for (double th = -180.0; th < 180.0; th += 0.25) {
            JumpSpec at = LineSpec.build(line, th, 0.5 * (p.rectXLo + p.rectXHi), 0.5 * (p.rectZLo + p.rectZHi));
            JumpPhysicsInputs sc = at.asScenario();
            double[] yaws = new double[sc.numTicks];
            Arrays.fill(yaws, th);
            double[] rs = FreeStartSolve.recoverStart(p.model, at, yaws);
            double v;
            if (rs != null) {
                v = FreeStartSolve.violationAt(p.model, at, yaws, rs[0], rs[1]);
            } else {
                double[] gf = sc.toGameFacings(yaws);
                v = JumpConstraintCompiler.compile(at).maxViolation(gf, p.model.forward(sc, gf));
            }
            if (v < bestViol) {
                bestViol = v;
                bestTheta = th;
            }
        }
        System.out.printf(Locale.ROOT, "held-yaw scan best theta=%.2f viol=%.4e%n", bestTheta, bestViol);
    }

    private static String env(String key, String dflt) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? dflt : v;
    }
}
