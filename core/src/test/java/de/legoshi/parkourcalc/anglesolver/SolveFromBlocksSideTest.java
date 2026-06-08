package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** jc-xt43: a multi-jump run that crosses an obstacle's keep-out band. "Solve from blocks" used to pick the
 *  keep-out side from the start position's proximity, so for collision (-238,4,-450) it demanded Z &lt;= -450.3
 *  (south) while the valid run actually passes north (Z ~ -448). The derived keep-outs must sit on the side
 *  the recorded (valid) run is on, so the recorded run satisfies every one of them. */
public class SolveFromBlocksSideTest {

    private static final long TIMEOUT_MS = 90_000L;
    private static final double TOL = 0.1; // separates a wrong-side miss (~0.6 block) from an edge graze (~0.005)

    @Test
    public void keepOutsLandOnTheSideTheRunActuallyPasses() {
        SaveFile file = SaveIO.parseSafe(readFixture("jc-xt43.json"));
        assertNotNull(file);
        assertNotNull(file.angleSolver);
        assertNotNull(file.debug);

        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.FAST); // the keep-out side is deterministic; FAST matches in-game
        state.clearResult();

        BoxController boxes = new BoxController();
        for (SaveFile.DebugTick d : file.debug) boxes.add(toTickState(d));

        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { },
                ExactJumpModel.forMcVersion(file.mcVersion));
        engine.solveFromBlocks();
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            sleep(10);
        }
        engine.poll();
        assertTrue("solve from blocks did not finish in time", !engine.isSolving());

        SolveResult result = state.getResult();
        assertNotNull("no result after solve from blocks", result);

        int checked = 0;
        int onWrongSide = 0;
        for (Integer tick : state.populatedTicks()) {
            TickConstraints tc = state.tickConstraintsOrNull(tick);
            if (tc == null) continue;
            TickState s = boxes.getState(tick);
            if (s == null) continue;
            for (Constraint c : tc.getConstraints()) {
                if (c.isRange()) continue; // footprints (be-inside) are not keep-outs
                double v = (c.getField() == Constraint.Field.X) ? s.position.x : s.position.z;
                boolean ok;
                switch (c.getOp()) {
                    case GE:
                    case GT:
                        ok = v >= c.getValue() - TOL;
                        break;
                    case LE:
                    case LT:
                        ok = v <= c.getValue() + TOL;
                        break;
                    default:
                        ok = true;
                }
                checked++;
                if (!ok) {
                    onWrongSide++;
                    System.out.printf("WRONG SIDE: keep-out %s %s %.3f at tick %d, but the valid run is at %.3f%n",
                            c.getField(), c.getOp(), c.getValue(), tick, v);
                }
            }
        }
        System.out.printf("checked %d derived keep-outs; %d on the wrong side%n", checked, onWrongSide);
        assertTrue("derived keep-outs are placed on the side the valid run actually passes", onWrongSide == 0);
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
        try (InputStream in = SolveFromBlocksSideTest.class.getResourceAsStream("/anglesolver/" + name)) {
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
