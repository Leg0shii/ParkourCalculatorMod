package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Locks in the angle solver before the refactor: each saved problem here was solved in-game
 * (the file's {@code angleSolver.result.success} is true), so re-running the live solver must
 * still find a fully-feasible solution. Drives the real {@link AngleSolverEngine} end-to-end
 * (load -> seed boxes from the recorded per-tick states -> background CMA-ES + bucket polish),
 * exactly as the Solve button does, and fails if the solver can no longer meet every constraint.
 *
 * <p>These are 1.8.9 captures; the model is the byte-exact {@link ExactJumpModel} for that version. The
 * test uses FAST effort: these jumps must stay solvable even on the smallest search budget.
 */
public class AngleSolverJumpTest {

    private static final long SOLVE_TIMEOUT_MS = 5_000L;

    @Test
    public void solvesJump154() {
        assertSolvable("j154.json");
    }

    @Test
    public void solvesJump1097() {
        assertSolvable("j1097.json");
    }

    @Test
    public void solvesJump121() {
        assertSolvable("j121.json");
    }

    /** A real 5-jump (land-and-rejump) capture whose recorded path satisfies all 13 constraints, but which
     *  the pre-per-tick-ground solver scored 7/13 (it hardcoded ground = before the first jump, so the
     *  later jumps' physics were wrong). Locks in that per-tick ground lets FAST find the feasible path. */
    @Test
    public void solvesJcXt43MultiJump() {
        assertSolvable("jc-xt43.json");
    }

    private static void assertSolvable(String fixture) {
        SaveFile file = SaveIO.parseSafe(readFixture(fixture));
        assertNotNull(fixture + ": failed to parse", file);
        assertNotNull(fixture + ": no angleSolver block", file.angleSolver);
        assertNotNull(fixture + ": no debug states (needed to seed the solve)", file.debug);

        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);

        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.FAST);
        state.clearResult();

        BoxController boxes = new BoxController();
        for (SaveFile.DebugTick d : file.debug) {
            boxes.add(toTickState(d));
        }

        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, ExactJumpModel.forMcVersion(file.mcVersion));

        engine.solve();
        long deadline = System.currentTimeMillis() + SOLVE_TIMEOUT_MS;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            sleep(10);
        }
        engine.poll();

        assertTrue(fixture + ": solve did not finish within " + SOLVE_TIMEOUT_MS + "ms", !engine.isSolving());

        SolveResult result = state.getResult();
        assertNotNull(fixture + ": no result after solve", result);
        assertTrue(fixture + ": solver met " + result.getMet() + "/" + result.getTotal() + " constraints",
                result.isSuccess());
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
        try (InputStream in = AngleSolverJumpTest.class.getResourceAsStream("/anglesolver/" + name)) {
            assertNotNull("missing test fixture: " + name, in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new RuntimeException("failed to read fixture " + name, e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
