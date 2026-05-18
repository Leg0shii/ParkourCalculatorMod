package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

public final class PlaybackController {

    // SimulatorEntity.resetPlayer() does tick(); tick(); before applying inputs.
    private static final int WARMUP_TICKS = 2;

    private final InputData inputData;
    private final SimulationRunner runner;
    private PlaybackBridge bridge;

    private boolean running;
    private int nextTick;
    private int warmupRemaining;

    public PlaybackController(InputData inputData, SimulationRunner runner) {
        this.inputData = inputData;
        this.runner = runner;
    }

    public void setBridge(PlaybackBridge bridge) {
        this.bridge = bridge;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean canStart() {
        return bridge != null && bridge.isSingleplayer() && inputData.size() > 0;
    }

    public String disabledReason() {
        if (bridge == null) return "Playback unavailable.";
        if (!bridge.isSingleplayer()) return "Playback is disabled in multiplayer.";
        if (inputData.size() == 0) return "Input list is empty.";
        return "";
    }

    public void start() {
        if (running) return;
        if (!canStart()) return;
        bridge.closeUI();
        bridge.teleport(runner.getStartPosition(), runner.getStartVelocity(), runner.getStartYaw());
        // Make sure no user-held key bleeds into the warmup ticks; the simulator's
        // warmup runs with an empty InputRow.
        bridge.releaseAllKeys();
        nextTick = 0;
        warmupRemaining = WARMUP_TICKS;
        running = true;
    }

    public void stop() {
        if (!running) return;
        running = false;
        warmupRemaining = 0;
        if (bridge != null) {
            bridge.releaseAllKeys();
        }
    }

    /** Loader calls each START_CLIENT_TICK. */
    public void tick() {
        if (!running || bridge == null) return;
        if (nextTick >= inputData.size()) {
            stop();
            return;
        }

        // Mirror SimulatorEntity.resetPlayer's two empty tick() calls so the real
        // player's onGround / prev* / velocity match the simulator's start state.
        if (warmupRemaining > 0) {
            bridge.releaseAllKeys();
            warmupRemaining--;
            return;
        }

        InputRow row = inputData.get(nextTick);
        for (InputRow.Key key : InputRow.Key.values()) {
            bridge.setKey(key, row.isKeyActive(key));
        }
        Float yaw = row.getYaw();
        if (yaw != null && yaw != 0f) {
            bridge.addYaw(yaw);
        }
        nextTick++;
    }
}
