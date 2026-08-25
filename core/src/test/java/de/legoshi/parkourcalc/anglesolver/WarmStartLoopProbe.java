package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

public class WarmStartLoopProbe {

    private static final double FLOOR = 0.01;

    @Test
    public void probe() throws Exception {
        String path = System.getenv("PKC_DIAG_FILE");
        Assume.assumeTrue("set PKC_DIAG_FILE", path != null && !path.isEmpty());
        int loops = Integer.parseInt(System.getenv().getOrDefault("PKC_LOOPS", "8"));
        int secs = Integer.parseInt(System.getenv().getOrDefault("PKC_SECS", "20"));

        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        int startTick = file.angleSolver.startTick;

        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setOptimizeSeconds(secs);

        BoxController boxes = Fixtures.buildBoxes(file);
        JumpSpec spec0 = new AngleSolverEngine(state, boxes, inputs, t -> { }, model).debugBuildSpec();
        double anchor = spec0.asScenario().startYaw;
        double bound = ClosedFormSolve.dualBound(spec0);
        out.printf("dual bound (fixed seed start) = %.6f   recorded in save = %.6f%n%n",
                bound, file.angleSolver.result != null ? file.angleSolver.result.objectiveValue : Double.NaN);
        out.printf("%-5s %-14s %-8s %-6s %-8s%n", "loop", "obj(X)", "success", "revs", "d-obj");

        double prev = Double.NaN;
        for (int i = 0; i < loops; i++) {
            AngleSolverState st = new AngleSolverState();
            SaveIO.applyAngleSolverTo(file, st);
            st.setOptimizeSeconds(secs);
            st.clearResult();
            AngleSolverEngine engine = new AngleSolverEngine(st, boxes, inputs, t -> { }, model);

            long t0 = System.currentTimeMillis();
            engine.solve();
            long deadline = t0 + (secs + 15L) * 1000L;
            while (engine.isSolving() && System.currentTimeMillis() < deadline) {
                engine.poll();
                Thread.sleep(20);
            }
            engine.poll();
            SolveResult r = st.getResult();
            if (r == null || r.getYaws() == null || r.getYaws().isEmpty()) {
                out.printf("%-5d null result%n", i);
                break;
            }
            List<SolveResult.YawEntry> ys = r.getYaws();
            double[] y = new double[ys.size()];
            for (int k = 0; k < ys.size(); k++) y[k] = ys.get(k).yaw;
            double obj = r.getObjectiveValue();
            out.printf("%-5d %-14.6f %-8s %-6d %-8s%n", i, obj, r.isSuccess(), reversals(anchor, y),
                    Double.isNaN(prev) ? "-" : String.format("%+.6f", obj - prev));
            prev = obj;

            boxes = resim(file, boxes, model, spec0.asScenario(), y, startTick);
        }

        out.flush();
        String report = sw.toString();
        System.out.println(report);
        File dst = new File("build/reports/warmstart-loop.txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), report.getBytes(StandardCharsets.UTF_8));
    }

    /** Apply the solved facings and "re-sim": rebuild the box trajectory so the window ticks carry the new
     *  positions/velocities/yaw (from the byte-exact forward), keeping every input unchanged. */
    private static BoxController resim(SaveFile file, BoxController old, ExactJumpModel model,
                                       JumpPhysicsInputs sc, double[] y, int startTick) {
        int n = y.length;
        double[] gf = sc.toGameFacings(Angles.wrapAll(y));
        ForwardPath p = model.forward(sc, gf);

        BoxController boxes = new BoxController();
        int size = old.size();
        for (int t = 0; t < size; t++) {
            TickState s = old.getState(t);
            int j = t - startTick;
            if (j >= 1 && j <= n) {
                Vec3dCore pos = new Vec3dCore(p.posX[j], p.posY[j], p.posZ[j]);
                Vec3dCore vel = new Vec3dCore(p.velX[j], p.velY[j], p.velZ[j]);
                float yaw = (float) gf[j - 1];
                boxes.add(new TickState(pos, s.onGround, s.sneaking, s.wallCollision, yaw,
                        Collections.<Vec3dCore>emptyList(), vel, s.softCollision, s.collisionAngleDegrees,
                        s.sprinting, s.moveForward, s.moveStrafe));
            } else {
                boxes.add(s);
            }
        }
        return boxes;
    }

    private static int reversals(double anchor, double[] y) {
        int c = 0, last = 0;
        double prev = anchor;
        for (double v : y) {
            double d = Angles.wrapDelta(v - prev);
            prev = v;
            if (Math.abs(d) <= FLOOR) continue;
            int s = d > 0 ? 1 : -1;
            if (last != 0 && s != last) c++;
            last = s;
        }
        return c;
    }
}
