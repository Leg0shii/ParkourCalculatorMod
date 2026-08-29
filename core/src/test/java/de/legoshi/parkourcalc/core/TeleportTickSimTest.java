package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TeleportTickSimTest {

    private static InputRow moving() {
        InputRow r = new InputRow();
        r.setKeyActive(InputRow.Key.W, true);
        r.setKeyActive(InputRow.Key.SPRINT, true);
        return r;
    }

    @Test
    public void teleportPlacesEntityAndZeroesHorizontalVelocity() {
        MediumWorldFakeSimulator sim = new MediumWorldFakeSimulator(new MediumWorldFakeSimulator.World());
        SimulationRunner runner = new SimulationRunner(sim);
        runner.setStartPosition(new Vec3dCore(0.0, 100.0, 0.0));
        runner.setStartVelocity(Vec3dCore.GROUND_REST_VELOCITY);
        runner.setStartYaw(0.0f);

        InputData data = new InputData();
        data.getRows().add(moving());
        data.getRows().add(moving());
        InputRow teleportRow = new InputRow();
        teleportRow.setTeleportEnabled(true);
        teleportRow.setTeleportDestination(50.0, 80.0, 50.0);
        data.getRows().add(teleportRow);
        data.getRows().add(new InputRow());

        List<TickState> path = runner.simulate(data);

        TickState afterTeleport = path.get(3);
        assertEquals("teleport pins X (horizontal velocity zeroed, no strafe on the teleport tick)",
                50.0, afterTeleport.position.x, 1.0e-9);
        assertEquals("teleport pins Z (horizontal velocity zeroed, no strafe on the teleport tick)",
                50.0, afterTeleport.position.z, 1.0e-9);
        assertEquals("Y falls by exactly the default rest velocity from the destination",
                80.0 + Vec3dCore.GROUND_REST_VELOCITY.y, afterTeleport.position.y, 1.0e-9);
    }

    @Test
    public void teleportRerunsIncrementallyFromTheTeleportTick() {
        MediumWorldFakeSimulator sim = new MediumWorldFakeSimulator(new MediumWorldFakeSimulator.World());
        SimulationRunner runner = new SimulationRunner(sim);
        runner.setStartPosition(new Vec3dCore(0.0, 100.0, 0.0));
        runner.setStartVelocity(Vec3dCore.GROUND_REST_VELOCITY);
        runner.setStartYaw(0.0f);

        InputData data = new InputData();
        data.getRows().add(moving());
        data.getRows().add(moving());
        InputRow teleportRow = new InputRow();
        data.getRows().add(teleportRow);
        data.getRows().add(new InputRow());

        runner.simulate(data);

        teleportRow.setTeleportEnabled(true);
        teleportRow.setTeleportDestination(50.0, 80.0, 50.0);
        assertTrue(runner.canResumeFrom(2));
        List<TickState> path = runner.simulateFrom(2, data);

        TickState afterTeleport = path.get(3);
        assertEquals(50.0, afterTeleport.position.x, 1.0e-9);
        assertEquals(50.0, afterTeleport.position.z, 1.0e-9);
        assertEquals(80.0 + Vec3dCore.GROUND_REST_VELOCITY.y, afterTeleport.position.y, 1.0e-9);
    }
}
