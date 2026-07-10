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
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

public class NixStartTickProbe {

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
        BoxController boxes = FixturesBoxes(file);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, model);

        int landing = state.getLandingTick();
        ps.printf("landingTick=%d (config startTick was %d)%n", landing, state.getStartTick());

        double rowYaw24 = rowYaw(file, 24);
        ps.printf("rows[24].yaw (user's 'tick 25' preset in the recording) = %.6f%n%n", rowYaw24);

        // Raw seam states from the row-replay sim (ground truth the solver seeds from).
        TickState s24 = boxes.getState(24);
        TickState s25 = boxes.getState(25);
        ps.printf("boxes.getState(24) pos=(%.8f,%.8f) vel=(%.8f,%.8f) yaw=%.4f sprint=%s sample=%s mf=%.3f ms=%.3f%n",
                s24.position.x, s24.position.z, s24.velocity.x, s24.velocity.z, s24.yaw, s24.sprinting,
                s24.hasMovementSample(), s24.moveForward, s24.moveStrafe);
        ps.printf("boxes.getState(25) pos=(%.8f,%.8f) vel=(%.8f,%.8f) yaw=%.4f sprint=%s sample=%s mf=%.3f ms=%.3f%n%n",
                s25.position.x, s25.position.z, s25.velocity.x, s25.velocity.z, s25.yaw, s25.sprinting,
                s25.hasMovementSample(), s25.moveForward, s25.moveStrafe);

        JumpSpec specA = buildAt(engine, state, 24, landing);
        JumpSpec specB = buildAt(engine, state, 25, landing);
        dumpSpec(ps, "RUN A (startTick=24, user 't25')", specA);
        dumpSpec(ps, "RUN B (startTick=25, user 't26')", specB);

        // ---- Seam reachability: does RUN A's model of the grounded tick 24 reproduce the sim seam s25? ----
        JumpPhysicsInputs scA = specA.asScenario();
        int nA = scA.numTicks;
        double[] yFull = new double[nA];
        yFull[0] = rowYaw24;
        ForwardPath pSeam = model.forward(scA, scA.toGameFacings(Angles.wrapAll(yFull)));
        ps.printf("%n--- SEAM REACHABILITY (RUN A model tick0=rows[24].yaw=%.4f) ---%n", rowYaw24);
        ps.printf("model posAfterTick0=(%.8f,%.8f) vel=(%.8f,%.8f)%n", pSeam.posX[1], pSeam.posZ[1], pSeam.velX[1], pSeam.velZ[1]);
        ps.printf("sim  getState(25)  =(%.8f,%.8f) vel=(%.8f,%.8f)%n", s25.position.x, s25.position.z, s25.velocity.x, s25.velocity.z);
        ps.printf("dPos=(%.3e,%.3e) dVel=(%.3e,%.3e)%n",
                pSeam.posX[1] - s25.position.x, pSeam.posZ[1] - s25.position.z,
                pSeam.velX[1] - s25.velocity.x, pSeam.velZ[1] - s25.velocity.z);

        // best tick0 yaw to reach s25 (in case rows[24].yaw is not what the sim used)
        double best = Double.POSITIVE_INFINITY, bestY = 0;
        for (int i = 0; i < 36000; i++) {
            double y = -180.0 + i * 0.01;
            yFull[0] = y;
            ForwardPath pp = model.forward(scA, scA.toGameFacings(Angles.wrapAll(yFull)));
            double d = Math.hypot(pp.posX[1] - s25.position.x, pp.posZ[1] - s25.position.z)
                    + Math.hypot(pp.velX[1] - s25.velocity.x, pp.velZ[1] - s25.velocity.z);
            if (d < best) { best = d; bestY = y; }
        }
        ps.printf("best tick0 yaw to hit seam s25: yaw=%.4f residual(dPos+dVel)=%.3e%n%n", bestY, best);

        // ---- BnB feasibility at each startTick ----
        String bn = System.getenv("PKC_BNB_NANOS");
        long budget = bn != null ? Long.parseLong(bn) : 60_000_000_000L;
        double[] yB = bnb(model, specB, budget);
        reportBnb(ps, model, "RUN B (st=25)", specB, yB);
        double[] yA = bnb(model, specA, budget);
        reportBnb(ps, model, "RUN A (st=24)", specA, yA);

        // ---- DECISIVE cross test: RUN B's feasible tail + real tick-24 yaw, forwarded in RUN A's spec ----
        if (yB != null) {
            double[] ya2 = new double[nA];
            ya2[0] = rowYaw24;
            for (int k = 0; k < yB.length && k + 1 < nA; k++) ya2[k + 1] = yB[k];
            forwardAndReport(ps, model, "CROSS [rows[24].yaw ++ RUN B tail] in RUN A spec", specA, ya2);

            ya2[0] = bestY;
            forwardAndReport(ps, model, "CROSS [bestSeamYaw ++ RUN B tail] in RUN A spec", specA, ya2);

            // ---- Feasible tick-24-yaw window WITH the tail FROZEN to RUN B's feasible solution ----
            // (lower bound on the true window: re-optimizing the tail can only widen it)
            JumpConstraintCompiler.Compiled compA = JumpConstraintCompiler.compile(specA);
            double loY = Double.NaN, hiY = Double.NaN;
            int feasCount = 0;
            double step = 0.0005;
            for (double y = bestY - 3.0; y <= bestY + 3.0; y += step) {
                ya2[0] = y;
                double[] gf = scA.toGameFacings(Angles.wrapAll(ya2));
                double v = compA.maxViolation(gf, model.forward(scA, gf));
                if (v <= 1.0e-9) {
                    feasCount++;
                    if (Double.isNaN(loY)) loY = y;
                    hiY = y;
                }
            }
            ps.printf("%n--- FEASIBLE tick0-yaw WINDOW (tail frozen to RUN B) ---%n");
            ps.printf("center(bestSeamYaw)=%.5f  feasible y in [%.5f, %.5f]  width=%.5f deg  (%d samples @ %.4f deg step)%n",
                    bestY, loY, hiY, Double.isNaN(loY) ? 0.0 : hiY - loY, feasCount, step);
        }
        if (outPath != null) { ps.flush(); ps.close(); }
    }

    private static BoxController FixturesBoxes(SaveFile file) {
        return de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file);
    }

    private static double rowYaw(SaveFile file, int idx) {
        double prev = 0;
        for (int k = 0; k <= idx && k < file.rows.size(); k++) {
            Float ry = file.rows.get(k).yaw;
            if (ry != null) prev = ry;
        }
        return prev;
    }

    private static JumpSpec buildAt(AngleSolverEngine engine, AngleSolverState state, int st, int landing) {
        state.setStartTick(st);
        state.setLandingTick(landing);
        return engine.debugBuildSpec();
    }

    private static void dumpSpec(PrintStream ps, String label, JumpSpec spec) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        StringBuilder pat = new StringBuilder();
        int jumps = 0; boolean prev = false;
        for (int t = 0; t < n; t++) {
            boolean grounded = !Double.isNaN(sc.slipAt(t));
            boolean jf = sc.jumpAt(t) && grounded;
            if (jf && !prev) jumps++;
            prev = jf;
            pat.append(jf ? 'J' : (grounded ? 'g' : '.'));
        }
        ps.printf("== %s ==%n  n=%d jumps=%d startPos=(%.8f,%.8f) v0=(%.8f,%.8f) startYaw=%.4f obj=%s/%s@%d cons=%d%n  pat=%s%n",
                label, n, jumps, sc.startPos.x, sc.startPos.z, sc.initialVelocity.x, sc.initialVelocity.z,
                sc.startYaw, spec.objective.axis, spec.objective.sense, spec.objective.tick, spec.constraints.size(), pat);
    }

    private static double[] bnb(ExactJumpModel model, JumpSpec spec, long budget) {
        return BoundPrunedRecovery.solve(model, spec, 0.0, new AtomicBoolean(false), budget, 1.0e300);
    }

    private static void reportBnb(PrintStream ps, ExactJumpModel model, String label, JumpSpec spec, double[] y) {
        if (y == null) {
            ps.printf("BnB %s: NULL (no feasible found in budget)%n%n", label);
            return;
        }
        forwardAndReport(ps, model, "BnB " + label, spec, y);
    }

    private static void forwardAndReport(PrintStream ps, ExactJumpModel model, String label, JumpSpec spec, double[] y) {
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(y));
        ForwardPath p = model.forward(sc, gf);
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(spec);
        double v = comp.maxViolation(gf, p);
        ps.printf("%s: maxViol=%.6e feasible=%s objX@%d=%.6f%n", label, v, v <= 1.0e-9, spec.objective.tick,
                p.getPos(spec.objective.tick, spec.objective.axis));
        for (JumpConstraint c : spec.constraints) {
            double slk = JumpConstraintCompiler.slack(c, gf, p);
            if (slk > 1.0e-9) ps.printf("    * VIOL %s t1=%d %s rhs=%.5f slack=%.6e%n", c.mode, c.t1, c.cmp, c.rhs, slk);
        }
        ps.println();
    }
}
