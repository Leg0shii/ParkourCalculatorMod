package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;

import java.util.List;

public final class PlaybackController {

    // SimulatorEntity.resetPlayer() does tick(); tick(); before applying inputs.
    private static final int WARMUP_TICKS = 2;

    // Cap a single render frame's dt to keep a long pause (window unfocus, GC) from
    // teleporting the eased yaw across its target in one step.
    private static final float MAX_FRAME_DT_SECONDS = 0.1f;

    private final InputData inputData;
    private final SimulationRunner runner;
    private final Settings settings;
    private PlaybackBridge bridge;

    private boolean running;
    private int nextTick;
    private int warmupRemaining;

    private float currentYaw;
    private float targetYaw;
    private long lastFrameNanos;

    private List<TickState> expectedPath;

    public PlaybackController(InputData inputData, SimulationRunner runner, Settings settings) {
        this.inputData = inputData;
        this.runner = runner;
        this.settings = settings;
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
        currentYaw = runner.getStartYaw();
        targetYaw = runner.getStartYaw();
        lastFrameNanos = 0L;
        expectedPath = runner.simulate(inputData);
        running = true;
        Vec3dCore startPos = runner.getStartPosition();
        System.out.println(String.format(
                "[pkc.playback] start rows=%d startPos=(%.4f,%.4f,%.4f) startYaw=%.3f flickSpeed=%.1f deg/s",
                inputData.size(), startPos.x, startPos.y, startPos.z, runner.getStartYaw(), settings.yawFlickSpeed));
    }

    public void stop() {
        if (!running) return;
        running = false;
        warmupRemaining = 0;
        lastFrameNanos = 0L;
        if (bridge != null) {
            bridge.releaseAllKeys();
        }
    }

    /** Loader calls each START_CLIENT_TICK. */
    public void tick() {
        if (!running || bridge == null) return;
        if (nextTick >= inputData.size()) {
            // Inputs exhausted; keep ticking so renderFrame() finishes easing to the
            // final target. Keys released so the player coasts rather than drifting.
            if (currentYaw == targetYaw) {
                logFinalComparison();
                stop();
            } else {
                bridge.releaseAllKeys();
            }
            return;
        }

        // Mirror SimulatorEntity.resetPlayer's two empty tick() calls so the real
        // player's onGround / prev* / velocity match the simulator's start state.
        if (warmupRemaining > 0) {
            bridge.releaseAllKeys();
            warmupRemaining--;
            return;
        }

        logTickComparison();

        InputRow row = inputData.get(nextTick);
        for (InputRow.Key key : InputRow.Key.values()) {
            bridge.setKey(key, row.isKeyActive(key));
        }
        Float yaw = row.getYaw();
        if (yaw != null && yaw != 0f) {
            targetYaw += yaw;
        }
        // Physics needs the simulator's prescribed yaw before MC's player.tick() runs
        // so position matches the sim. renderFrame() overwrites with the eased yaw
        // before any camera sampling, so the snap stays invisible.
        bridge.setYaw(targetYaw);
        nextTick++;
    }

    private void logFinalComparison() {
        if (expectedPath == null || expectedPath.isEmpty()) return;
        TickState expected = expectedPath.get(expectedPath.size() - 1);
        Vec3dCore actualPos = bridge.getPosition();
        float actualYaw = bridge.getYaw();
        double dx = actualPos.x - expected.position.x;
        double dy = actualPos.y - expected.position.y;
        double dz = actualPos.z - expected.position.z;
        double drift = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float yawDelta = actualYaw - expected.yaw;
        System.out.println(String.format(
                "[pkc.playback] final expectPos=(%.4f,%.4f,%.4f) expectYaw=%.3f actualPos=(%.4f,%.4f,%.4f) actualYaw=%.3f drift=%.4f yawDelta=%.3f",
                expected.position.x, expected.position.y, expected.position.z, expected.yaw,
                actualPos.x, actualPos.y, actualPos.z, actualYaw,
                drift, yawDelta));
    }

    private void logTickComparison() {
        if (expectedPath == null || nextTick >= expectedPath.size()) return;
        TickState expected = expectedPath.get(nextTick);
        Vec3dCore actualPos = bridge.getPosition();
        float actualYaw = bridge.getYaw();
        double dx = actualPos.x - expected.position.x;
        double dy = actualPos.y - expected.position.y;
        double dz = actualPos.z - expected.position.z;
        double drift = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float yawDelta = actualYaw - expected.yaw;
        System.out.println(String.format(
                "[pkc.playback] tick=%d expectPos=(%.4f,%.4f,%.4f) expectYaw=%.3f actualPos=(%.4f,%.4f,%.4f) actualYaw=%.3f drift=%.4f yawDelta=%.3f current=%.3f target=%.3f",
                nextTick,
                expected.position.x, expected.position.y, expected.position.z, expected.yaw,
                actualPos.x, actualPos.y, actualPos.z, actualYaw,
                drift, yawDelta, currentYaw, targetYaw));
    }

    /** Loader calls after MC's physics tick. Re-applies the visual eased yaw so
     *  the next render frame doesn't briefly show the snapped physics yaw. */
    public void postTick() {
        if (!running || bridge == null) return;
        bridge.setYaw(currentYaw);
    }

    /** Loader calls each render frame; advances the constant-angular-velocity yaw ease. */
    public void renderFrame() {
        if (!running || bridge == null) return;
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return;
        }
        float dt = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        if (dt > MAX_FRAME_DT_SECONDS) dt = MAX_FRAME_DT_SECONDS;

        float delta = targetYaw - currentYaw;
        if (delta == 0f) return;
        float step = settings.yawFlickSpeed * dt;
        if (step >= Math.abs(delta)) {
            currentYaw = targetYaw;
        } else {
            currentYaw += Math.signum(delta) * step;
        }
        bridge.setYaw(currentYaw);
    }
}
