package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

public class NixSetupBasinProbe {

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
        state.setStartTick(24); state.setLandingTick(landing);
        JumpSpec specA = engine.debugBuildSpec();
        state.setStartTick(25); state.setLandingTick(landing);
        JumpSpec specB = engine.debugBuildSpec();
        JumpPhysicsInputs scA = specA.asScenario();
        JumpPhysicsInputs scB = specB.asScenario();

        double stepDeg = envD("PKC_STEP_DEG", 6.0);
        long perBudget = (long) (envD("PKC_TAIL_SEC", 4.0) * 1e9);
        ps.printf("BASIN SCAN: fix tick0 yaw, forward one grounded tick from debug[24], then solve the tail%n");
        ps.printf("            (== what you do when you preset the angle and re-solve from t26)%n");
        ps.printf("grid step=%.1f deg  tail BnB budget=%.1fs each%n%n", stepDeg, perBudget / 1e9);

        double[] yFull = new double[scA.numTicks];
        int total = 0, feas = 0;
        StringBuilder feasRanges = new StringBuilder();
        boolean inRun = false; double runLo = 0, prevY = 0;

        String explicit = System.getenv("PKC_YAWS");
        double[] grid;
        if (explicit != null && !explicit.isEmpty()) {
            String[] parts = explicit.split(",");
            grid = new double[parts.length];
            for (int i = 0; i < parts.length; i++) grid[i] = Double.parseDouble(parts[i].trim());
        } else {
            int cnt = (int) Math.floor(360.0 / stepDeg);
            grid = new double[cnt];
            for (int i = 0; i < cnt; i++) grid[i] = -180.0 + i * stepDeg;
        }
        for (double y : grid) {
            yFull[0] = y;
            ForwardPath p0 = model.forward(scA, scA.toGameFacings(Angles.wrapAll(yFull)));
            Vec3dCore seamPos = new Vec3dCore(p0.posX[1], p0.posY[1], p0.posZ[1]);
            Vec3dCore seamVel = new Vec3dCore(p0.velX[1], p0.velY[1], p0.velZ[1]);
            JumpSpec tail = reseed(scB, specB, seamPos, seamVel);
            double[] y2 = BoundPrunedRecovery.solve(model, tail, 0.0, new AtomicBoolean(false), perBudget, 1.0e300);
            boolean ok = false;
            double objX = Double.NaN;
            if (y2 != null) {
                double[] gf = tail.asScenario().toGameFacings(Angles.wrapAll(y2));
                ForwardPath pp = model.forward(tail.asScenario(), gf);
                double v = JumpConstraintCompiler.compile(tail).maxViolation(gf, pp);
                ok = v <= 1.0e-9;
                objX = pp.getPos(tail.objective.tick, tail.objective.axis);
            }
            total++;
            if (ok) feas++;
            ps.printf("  tick0=%+8.2f  seam=(%.5f,%.5f) vel=(%.5f,%.5f)  tailFeasible=%s  objX=%.5f%n",
                    y, seamPos.x, seamPos.z, seamVel.x, seamVel.z, ok, objX);
            if (ok && !inRun) { inRun = true; runLo = y; }
            if (!ok && inRun) { inRun = false; feasRanges.append(String.format("[%.1f,%.1f] ", runLo, prevY)); }
            prevY = y;
        }
        if (inRun) feasRanges.append(String.format("[%.1f,%.1f] ", runLo, prevY));
        ps.printf("%nFEASIBLE tick0 fraction = %d/%d = %.0f%%%n", feas, total, 100.0 * feas / total);
        ps.printf("feasible tick0 ranges (>=BnB budget): %s%n", feasRanges);
        if (outPath != null) { ps.flush(); ps.close(); }
    }

    private static JumpSpec reseed(JumpPhysicsInputs b, JumpSpec base, Vec3dCore pos, Vec3dCore vel) {
        JumpPhysicsInputs a = new JumpPhysicsInputs(b.numTicks);
        a.startPos = pos;
        a.startYaw = b.startYaw;
        a.initialVelocity = vel;
        a.startBox = StartBox.pinned(pos.x, pos.z, vel.x, vel.z);
        a.jumpTick = b.jumpTick;
        a.jumpPerTick = b.jumpPerTick;
        a.strafeSign = b.strafeSign;
        a.strafePerTick = b.strafePerTick;
        a.speedAmplifier = b.speedAmplifier;
        a.slipPerTick = b.slipPerTick;
        a.yawLockedPerTick = b.yawLockedPerTick;
        a.sprintPerTick = b.sprintPerTick;
        a.incomingSprint = b.incomingSprint;
        a.incomingAmp = b.incomingAmp;
        a.forwardInputPerTick = b.forwardInputPerTick;
        a.strafeInputPerTick = b.strafeInputPerTick;
        return new JumpSpec(a, base.constraints, base.objective);
    }

    private static double envD(String k, double def) {
        String v = System.getenv(k);
        return v == null || v.isEmpty() ? def : Double.parseDouble(v);
    }
}
