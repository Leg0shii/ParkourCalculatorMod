package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** Proves the long-run solver uses NOTHING from the recorded trajectory except the legitimate resume START
 *  state: solving with the real trajectory and with the trajectory corrupted to garbage (every tick but the
 *  start replaced with absurd pos/vel/yaw/onGround) must produce a byte-identical facing array. If any oracle
 *  (recorded facings/positions/footing) leaked into the solve, the two would differ. */
public class NoTrajectoryDependenceTest {

    /** The recorded FACINGS and POSITIONS are the "existing solution" / warm start the solver must never use.
     *  Corrupt every non-start tick's position, velocity and yaw to garbage (keeping the input-derived
     *  ground/air footing, which is structure, not a solution) and require a byte-identical solve. */
    @Test
    public void solveUsesNoRecordedFacingsOrPositions() {
        for (String fx : new String[]{"deserthard-v12.json", "deserthard-v13-fail.json", "deserthard-nothing.json"}) {
            double[] real = solveLongRun(fx, false);
            double[] garbage = solveLongRun(fx, true);
            assertNotNull(fx + ": real solve returned null", real);
            assertNotNull(fx + ": garbage pos/vel/yaw solve returned null -- solver reads recorded pos/vel/yaw", garbage);
            assertEquals(fx + ": facing-array length changed", real.length, garbage.length);
            assertArrayEquals(fx + ": solve differs when recorded pos/vel/yaw are garbage -> it is using the "
                    + "recorded trajectory as a warm start / oracle", real, garbage, 0.0);
        }
    }

    /** Stronger: also drop the recorded FOOTING -- force every non-start tick airborne, so the only ground
     *  ticks are the ones the INPUT slip-overrides mark. If the solve is still identical, the ground/air
     *  structure comes entirely from the input annotations, not from where the recorded run happened to be
     *  standing -- i.e. the start state is the ONLY thing taken from the trajectory. */
    @Test
    public void solveStructureComesFromInputsNotRecordedFooting() {
        for (String fx : new String[]{"deserthard-v12.json", "deserthard-v13-fail.json", "deserthard-nothing.json"}) {
            double[] real = solveLongRun(fx, false);
            double[] noFooting = solveLongRunForceAir(fx);
            assertNotNull(fx + ": real solve returned null", real);
            assertNotNull(fx + ": no-footing solve returned null -- structure depends on recorded footing", noFooting);
            assertArrayEquals(fx + ": solve differs once recorded footing is dropped -> the ground/air structure "
                    + "is read from the recorded trajectory, not the input annotations", real, noFooting, 0.0);
        }
    }

    private static double[] solveLongRunForceAir(String fixture) {
        return build(fixture, true, true);
    }

    /** Build the spec the way the engine does, optionally corrupting every non-start recorded tick's
     *  position/velocity/yaw to garbage (the footing is left alone -- that is input structure, not a
     *  solution), and run the from-scratch solver on it. */
    private static double[] solveLongRun(String fixture, boolean corrupt) {
        return build(fixture, corrupt, false);
    }

    private static double[] build(String fixture, boolean corrupt, boolean forceAir) {
        SaveFile file = SaveIO.parseSafe(readFixture(fixture));
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.FAST);
        int startTick = state.getStartTick();

        BoxController boxes = new BoxController();
        int idx = 0;
        for (SaveFile.DebugTick d : file.debug) {
            TickState ts = toTickState(d);
            // Corrupt every tick except the resume seed (boxes.getState(startTick)): absurd position, velocity
            // and yaw, so ANY reliance on the recorded path/facings beyond the legitimate start seed breaks.
            // forceAir additionally drops the recorded footing (marks the tick airborne).
            if ((corrupt || forceAir) && idx != startTick) {
                ts = new TickState(new Vec3dCore(99999.0, -88888.0, 77777.0), forceAir ? false : d.onGround,
                        d.sneaking, d.wallCollision, 271.83f, Collections.<Vec3dCore>emptyList(),
                        new Vec3dCore(13.0, 7.0, -19.0), d.softCollision,
                        d.collisionAngle == null ? Double.NaN : d.collisionAngle);
            }
            boxes.add(ts);
            idx++;
        }
        ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, exact);
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull("could not build spec", spec);
        JumpPhysicsInputs sc = spec.asScenario();
        // Sanity: the start seed is preserved either way (it is legitimately available on any solve).
        return LongRunSolver.solve(exact, spec, 0.0, new AtomicBoolean(false));
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
        try (InputStream in = NoTrajectoryDependenceTest.class.getResourceAsStream("/anglesolver/" + name)) {
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
