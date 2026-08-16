package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TickTeleportTest {

    private static InputData rows(int n) {
        InputData data = new InputData();
        for (int i = 0; i < n; i++) {
            data.getRows().add(new InputRow());
        }
        return data;
    }

    private static PlaybackController controller(FakePlaybackBridge bridge, InputData data) {
        PlaybackController pc = new PlaybackController(data, new SimulationRunner(new FakeSimulator()), new Settings());
        if (bridge != null) pc.setBridge(bridge);
        return pc;
    }

    @Test
    public void teleportsToTheGivenTickStateWithZeroVelocityAndNoCarry() {
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        PlaybackController pc = controller(bridge, rows(3));

        Vec3dCore pos = new Vec3dCore(12.5, 65.0, -3.25);
        assertTrue(pc.canTeleportToTick());
        assertTrue(pc.teleportToTick(pos, 135f, -20f));

        assertEquals(1, bridge.teleportCalls);
        assertEquals(pos, bridge.teleportPos);
        assertEquals(Vec3dCore.ZERO, bridge.teleportVel);
        assertEquals(135f, bridge.teleportYaw, 0f);
        assertNull("plain teleport carries no checkpoint state", bridge.teleportCarry);
        assertEquals(-20f, bridge.pitch, 0f);
    }

    @Test
    public void multiplayerBlocksTeleportEvenWhenCalledDirectly() {
        FakePlaybackBridge bridge = new FakePlaybackBridge() {
            @Override
            public boolean isSingleplayer() {
                return false;
            }
        };
        PlaybackController pc = controller(bridge, rows(3));

        assertFalse(pc.canTeleportToTick());
        assertFalse(pc.teleportToTick(new Vec3dCore(1, 2, 3), 0f, 0f));
        assertEquals("the bridge must never see a multiplayer teleport", 0, bridge.teleportCalls);
    }

    @Test
    public void missingBridgeBlocksTeleport() {
        PlaybackController pc = controller(null, rows(3));
        assertFalse(pc.canTeleportToTick());
        assertFalse(pc.teleportToTick(new Vec3dCore(1, 2, 3), 0f, 0f));
    }

    @Test
    public void runningPlaybackBlocksTeleport() {
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        InputData data = rows(4);
        PlaybackController pc = controller(bridge, data);

        pc.start(0, data.size(), Vec3dCore.ZERO, Vec3dCore.ZERO, 0f);
        assertTrue(pc.isRunning());
        assertEquals(1, bridge.teleportCalls);

        assertFalse(pc.canTeleportToTick());
        assertFalse(pc.teleportToTick(new Vec3dCore(1, 2, 3), 0f, 0f));
        assertEquals(1, bridge.teleportCalls);

        pc.stop();
        assertTrue(pc.canTeleportToTick());
    }
}
