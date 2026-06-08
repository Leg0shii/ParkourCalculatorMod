package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;

/** Not an assertion test: times the live engine on each fixture under FAST effort and prints
 *  median/min/max ms + feasibility, so we can compare the solver before/after a change. */
public class SolveBenchmark {

    private static final String[] FIXTURES = {"j121.json", "j154.json", "j1097.json"};
    private static final int WARMUP = 2;
    private static final int RUNS = 7;

    @org.junit.Ignore("manual benchmark, not an assertion test")
    @Test
    public void benchmark() {
        de.legoshi.parkourcalc.core.anglesolver.solver.FastSolve.DEBUG = true;
        for (String fx : FIXTURES) {
            for (int i = 0; i < WARMUP; i++) solveOnce(fx);
            long[] times = new long[RUNS];
            boolean allOk = true;
            int met = 0, total = 0;
            for (int i = 0; i < RUNS; i++) {
                long t0 = System.nanoTime();
                SolveResult r = solveOnce(fx);
                times[i] = (System.nanoTime() - t0) / 1_000_000L;
                allOk &= (r != null && r.isSuccess());
                if (r != null) { met = r.getMet(); total = r.getTotal(); }
            }
            Arrays.sort(times);
            System.out.printf("BENCH %-14s median=%4dms min=%4dms max=%4dms  feasible=%s (%d/%d)%n",
                    fx, times[RUNS / 2], times[0], times[RUNS - 1], allOk, met, total);
        }
    }

    private static SolveResult solveOnce(String fixture) {
        SaveFile file = SaveIO.parseSafe(readFixture(fixture));
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.FAST);
        state.clearResult();
        BoxController boxes = new BoxController();
        for (SaveFile.DebugTick d : file.debug) boxes.add(toTickState(d));
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { },
                ExactJumpModel.forMcVersion(file.mcVersion));
        engine.solve();
        long deadline = System.currentTimeMillis() + 30_000L;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        engine.poll();
        return state.getResult();
    }

    private static TickState toTickState(SaveFile.DebugTick d) {
        Vec3dCore pos = new Vec3dCore(d.pos[0], d.pos[1], d.pos[2]);
        Vec3dCore vel = (d.vel != null && d.vel.length >= 3)
                ? new Vec3dCore(d.vel[0], d.vel[1], d.vel[2]) : Vec3dCore.ZERO;
        double angle = d.collisionAngle == null ? Double.NaN : d.collisionAngle;
        return new TickState(pos, d.onGround, d.sneaking, d.wallCollision, d.yaw,
                Collections.<Vec3dCore>emptyList(), vel, d.softCollision, angle);
    }

    private static String readFixture(String name) {
        try (InputStream in = SolveBenchmark.class.getResourceAsStream("/anglesolver/" + name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("failed to read fixture " + name, e);
        }
    }
}
