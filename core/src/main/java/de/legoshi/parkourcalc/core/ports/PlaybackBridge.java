package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;

/**
 * Loader-side driver for "real-player input playback". Core holds the schedule
 * and tick counter; the bridge does the MC-touching work each tick.
 *
 * Multiplayer is refused at start() in the controller via isSingleplayer().
 */
public interface PlaybackBridge {

    boolean isSingleplayer();

    void teleport(Vec3dCore pos, Vec3dCore vel, float yaw);

    void setKey(InputRow.Key key, boolean pressed);

    void addYaw(float deltaYaw);

    void releaseAllKeys();

    /** Close any open mod UI (control panel / GuiScreen) the same way the toggle key would. */
    void closeUI();
}
