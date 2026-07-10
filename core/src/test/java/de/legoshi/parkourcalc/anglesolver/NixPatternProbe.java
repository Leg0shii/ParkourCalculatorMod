package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

public class NixPatternProbe {

    @Test
    public void probe() throws Exception {
        String path = System.getenv("PKC_SOLVE_FILE");
        org.junit.Assume.assumeTrue("set PKC_SOLVE_FILE", path != null && !path.isEmpty());
        String outPath = System.getenv("PKC_OUT");
        PrintStream ps = outPath != null ? new PrintStream(new FileOutputStream(outPath), true, "UTF-8") : System.out;

        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        BoxController boxes = de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, model);
        int landing = state.getLandingTick();

        state.setStartTick(25); state.setLandingTick(landing);
        JumpSpec specB = engine.debugBuildSpec();
        state.setStartTick(24); state.setLandingTick(landing);
        JumpSpec specA = engine.debugBuildSpec();
        JumpPhysicsInputs scA = specA.asScenario();
        int nA = scA.numTicks;

        // feasible tail (t26) via BnB
        double[] yB = BoundPrunedRecovery.solve(model, specB, 0.0, new AtomicBoolean(false), 60_000_000_000L, 1.0e300);
        if (yB == null) { ps.println("no feasible tail found; abort"); if (outPath != null) ps.close(); return; }

        // reconstruct a feasible t25: sweep tick0 with the tail frozen to yB
        JumpConstraintCompiler.Compiled compA = JumpConstraintCompiler.compile(specA);
        double[] ya = new double[nA];
        for (int k = 0; k + 1 < nA && k < yB.length; k++) ya[k + 1] = yB[k];
        double bestY = 0, bestV = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 36000; i++) {
            double y = -180.0 + i * 0.01;
            ya[0] = y;
            double[] gf = scA.toGameFacings(Angles.wrapAll(ya));
            double v = compA.maxViolation(gf, model.forward(scA, gf));
            if (v < bestV) { bestV = v; bestY = y; }
        }
        ya[0] = bestY;
        double[] gfa = scA.toGameFacings(Angles.wrapAll(ya));
        double feasViol = compA.maxViolation(gfa, model.forward(scA, gfa));
        ps.printf("feasible t25 reconstructed: tick0=%.4f viol=%.5e%n", bestY, feasViol);

        // its zeroing pattern
        boolean[] zx = new boolean[nA], zz = new boolean[nA];
        new JumpLinearModel(scA).zeroingPattern(Angles.wrapAll(ya), model.inertiaThreshold(), model.perAxisInertia(), zx, zz);
        ps.printf("feasible t25 zeroing pattern: %s%n", patLabel(zx, zz));

        // FEED that exact pattern to the dual on t25
        double[] certA = ClosedFormSolve.optimizeWithPattern(model, specA, 0.0, new AtomicBoolean(false), zx, zz);
        ps.printf(">>> dual on t25 WITH the feasible pattern: %s%n",
                certA == null ? "did NOT certify (null)" : "CERTIFIED feasible");

        // control: the dual's own free run on t25 (should miss)
        double[] freeA = ClosedFormSolve.optimize(model, specA, 0.0, new AtomicBoolean(false));
        ps.printf("    control: dual free run on t25: %s%n", freeA == null ? "miss (null)" : "certified");

        // sanity: feasible tail's own pattern fed to t26 (should certify)
        JumpPhysicsInputs scB = specB.asScenario();
        boolean[] zxB = new boolean[scB.numTicks], zzB = new boolean[scB.numTicks];
        new JumpLinearModel(scB).zeroingPattern(Angles.wrapAll(yB), model.inertiaThreshold(), model.perAxisInertia(), zxB, zzB);
        ps.printf("feasible t26 zeroing pattern: %s%n", patLabel(zxB, zzB));
        double[] certB = ClosedFormSolve.optimizeWithPattern(model, specB, 0.0, new AtomicBoolean(false), zxB, zzB);
        ps.printf("    sanity: dual on t26 WITH its feasible pattern: %s%n",
                certB == null ? "did NOT certify" : "CERTIFIED");

        // FIX PROTOTYPE: pin the first K feasible positions (seg1..K) so the dual only solves the tail beyond.
        // Finds how much of the prefix must be committed before the dual can certify (the coupling depth).
        ForwardPath fp = model.forward(scA, gfa);
        ps.printf("%nfeasible seam @seg1 = (%.5f, %.5f)  vel@seg1 = (%.5f, %.5f)%n",
                fp.posX[1], fp.posZ[1], fp.velX[1], fp.velZ[1]);
        double h = 0.01;
        for (int K : new int[]{1, 2, 3, 5, 8, 12}) {
            java.util.List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint> cons =
                    new java.util.ArrayList<>(specA.constraints);
            for (int t = 1; t <= K; t++) {
                cons.add(mk(de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Mode.X, t, fp.posX[t] - h, false));
                cons.add(mk(de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Mode.X, t, fp.posX[t] + h, true));
                cons.add(mk(de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Mode.Z, t, fp.posZ[t] - h, false));
                cons.add(mk(de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Mode.Z, t, fp.posZ[t] + h, true));
            }
            JumpSpec pinned = new JumpSpec(scA, cons, specA.objective);
            double[] cert = ClosedFormSolve.optimize(model, pinned, 0.0, new AtomicBoolean(false));
            String info = "-";
            if (cert != null) {
                double[] g = scA.toGameFacings(Angles.wrapAll(cert));
                double v = JumpConstraintCompiler.compile(specA).maxViolation(g, model.forward(scA, g));
                info = String.format("tick0=%.4f origViol=%.2e", cert[0], v);
            }
            ps.printf(">>> dual on t25, first %2d positions pinned (+/-%.3f): %s  %s%n",
                    K, h, cert == null ? "did NOT certify" : "CERTIFIED", info);
        }

        if (outPath != null) { ps.flush(); ps.close(); }
    }

    private static de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint mk(
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Mode mode, int t1, double rhs, boolean le) {
        return new de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint(mode, t1, null,
                de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Op.PLUS,
                le ? de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Cmp.LE
                   : de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Cmp.GE,
                rhs, "seam");
    }

    private static String patLabel(boolean[] zx, boolean[] zz) {
        StringBuilder sb = new StringBuilder();
        sb.append("x@"); appendRuns(sb, zx); sb.append(" z@"); appendRuns(sb, zz);
        return sb.toString();
    }

    private static void appendRuns(StringBuilder sb, boolean[] b) {
        boolean first = true;
        int i = 0;
        while (i < b.length) {
            if (!b[i]) { i++; continue; }
            int j = i;
            while (j + 1 < b.length && b[j + 1]) j++;
            if (!first) sb.append(',');
            sb.append(i == j ? String.valueOf(i) : i + "-" + j);
            first = false;
            i = j + 1;
        }
        if (first) sb.append("none");
    }
}
