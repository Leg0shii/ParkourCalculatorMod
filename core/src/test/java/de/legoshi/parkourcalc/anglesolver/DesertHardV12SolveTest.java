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
import java.util.Collections;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** End-to-end: the long 354-tick "desert hard v12" run (30 jumps, 81 X/Z constraints) must SOLVE through the
 *  engine -- the closed-form dual cannot certify it at scale, so the long-run feasibility fallback restores a
 *  byte-exact-feasible facing assignment from the editor's current trajectory. Before this work the engine
 *  reported "no solution" (and dropped into a ~100 s, hopeless 354-dim CMA-ES). */
public class DesertHardV12SolveTest {

    @Test
    public void v12SolvesByteExact() {
        SaveFile file = SaveIO.parseSafe(readFixture("deserthard-v12.json"));
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

        long t0 = System.nanoTime();
        engine.solve();
        long deadline = System.currentTimeMillis() + 60_000L;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        engine.poll();
        double ms = (System.nanoTime() - t0) / 1e6;

        SolveResult r = state.getResult();
        assertNotNull("engine returned no result", r);
        System.out.printf("V12 engine solve: success=%s met=%d/%d  %.0f ms%n",
                r.isSuccess(), r.getMet(), r.getTotal(), ms);
        assertTrue("v12 did not solve (" + r.getMet() + "/" + r.getTotal() + " met) -- the run is feasible, "
                + "the fallback must restore it", r.isSuccess());
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
        try (InputStream in = DesertHardV12SolveTest.class.getResourceAsStream("/anglesolver/" + name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int k;
            while ((k = in.read(buf)) != -1) out.write(buf, 0, k);
            return out.toString("UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
