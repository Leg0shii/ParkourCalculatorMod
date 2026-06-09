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

import static org.junit.Assert.assertTrue;

/** Regression for the "Solving for a different Solve-For yields no solution" bug on the long desert-hard
 *  jump (real user saves, debug-enabled). Whether a solution EXISTS is a property of the constraints, not of
 *  the optimized direction, so every Solve-For (MIN/MAX x X/Z) must report a solution on a solvable jump.
 *
 *  <p>Before the fix the engine certified only the one direction whose objective-optimal vertex happened to
 *  be byte-exact feasible and dropped the rest into the effectively-hopeless 189-dimensional CMA-ES (~20 s,
 *  no solution). The engine now tries the other directions via the same microsecond closed form, so all four
 *  resolve in well under a second. */
public class DesertHardSolveForTest {

    @Test
    public void everySolveForFindsASolution_v7() {
        assertAllDirectionsSolve("deserthard-v7.json");
    }

    @Test
    public void everySolveForFindsASolution_vfail() {
        assertAllDirectionsSolve("deserthard-vfail.json");
    }

    private static void assertAllDirectionsSolve(String fixture) {
        SaveFile file = SaveIO.parseSafe(readFixture(fixture));
        for (AngleSolverState.Axis axis : AngleSolverState.Axis.values()) {
            for (AngleSolverState.Goal goal : AngleSolverState.Goal.values()) {
                // Fresh engine per direction so nothing leaks between solves.
                InputData inputs = new InputData();
                SaveIO.applyRowsTo(file, inputs);
                AngleSolverState state = new AngleSolverState();
                SaveIO.applyAngleSolverTo(file, state);
                state.setEffort(AngleSolverState.Effort.FAST);
                state.setAxis(axis);
                state.setGoal(goal);
                state.clearResult();
                BoxController boxes = new BoxController();
                for (SaveFile.DebugTick d : file.debug) boxes.add(toTickState(d));
                ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
                AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, exact);

                engine.solve();
                long deadline = System.currentTimeMillis() + 60_000L;
                while (engine.isSolving() && System.currentTimeMillis() < deadline) {
                    engine.poll();
                    try { Thread.sleep(3); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                engine.poll();

                SolveResult r = state.getResult();
                String dir = axis + "/" + goal;
                assertTrue(fixture + " " + dir + ": engine returned no result", r != null);
                assertTrue(fixture + " " + dir + ": no solution (" + r.getMet() + "/" + r.getTotal()
                        + " constraints met) -- the jump is solvable, so every Solve-For must find one",
                        r.isSuccess());
            }
        }
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
        try (InputStream in = DesertHardSolveForTest.class.getResourceAsStream("/anglesolver/" + name)) {
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
