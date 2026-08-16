package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;

/**
 * Loader-side driver for "real-player input playback". Core holds the schedule
 * and tick counter; the bridge does the MC-touching work each tick.
 *
 * Multiplayer is refused at start() in the controller via isSingleplayer(),
 * unless the bridge opts in via supportsMultiplayerPlayback().
 */
public interface PlaybackBridge {

    boolean isSingleplayer();

    default boolean supportsMultiplayerPlayback() {
        return false;
    }

    /** Whether the client is paused (e.g. the Esc menu in singleplayer). While paused the world does
     *  not tick, so playback must freeze in place instead of consuming its schedule (gh-106). */
    default boolean isGamePaused() {
        return false;
    }

    void teleport(Vec3dCore pos, Vec3dCore vel, float yaw, Checkpoint carry);

    void setKey(InputRow.Key key, boolean pressed);

    void setYaw(float absoluteYaw);

    default void setHeadYaw(float absoluteYaw) {}

    default void setPitch(float absolutePitch) {}

    void releaseAllKeys();

    default void suppressFlight() {}

    /** Close any open mod UI (control panel / GuiScreen) the same way the toggle key would. */
    void closeUI();

    void applyEffects(int speedAmplifier, int jumpBoostAmplifier);

    /** Switch the held hotbar slot (0-8) so playback places from the right slot. No-op if unsupported. */
    default void setHotbarSlot(int slotZeroBased) {}

    default void dumpPlayerState(int tickIndex) {}

    default void beginPlaybackCapture() {}

    default void capturePlaybackSample(int tickIndex) {}

    default void finishPlaybackCapture() {}
}
