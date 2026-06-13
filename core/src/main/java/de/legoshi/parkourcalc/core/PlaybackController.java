package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.DebugFlags;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;

import java.util.ArrayList;
import java.util.List;

public final class PlaybackController {

    // SimulatorEntity.resetPlayer() does tick(); tick(); before applying inputs.
    private static final int WARMUP_TICKS = 2;

    private static final long TICK_NANOS = 50_000_000L;
    private static final float MAX_FRAME_DT_SECONDS = 0.1f;

    private final InputData inputData;
    private final SimulationRunner runner;
    private final Settings settings;
    private PlaybackBridge bridge;

    private boolean running;
    private int nextTick;
    private int warmupRemaining;
    private int lastSpeedAmplifier;
    private int lastJumpBoostAmplifier;

    private boolean firstTickOnGround;

    // currentTickYaw is the physics yaw, kept bit-identical to the simulator's rotationYaw:
    // the sine table quantizes 45 and -315 into different buckets, so a mod-360 offset
    // drifts the replayed path off the simulated one. The short-way turn on locked rows
    // lives only in the display endpoints below.
    private float currentTickYaw;
    // Lerp endpoints displayedYaw chases; congruent to currentTickYaw mod 360.
    private float prevTickYaw;
    private float displayTargetYaw;
    private long tickEndNanos;

    // displayedYaw is the camera yaw; it equals the ideal lerp value when the
    // recording's angular speed is under the cap, otherwise lags at the cap rate.
    private float displayedYaw;
    private long lastFrameNanos;

    // Non-zero while the game sits paused: client ticks keep firing but the world does not advance,
    // so the schedule must freeze with it (gh-106). On resume the lerp clocks shift by the pause.
    private long pausedAtNanos;

    private final List<String> simTickDumps = new ArrayList<String>();

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

    public int currentTick() {
        if (!running) return -1;
        if (warmupRemaining > 0) return -1;
        return nextTick - 1;
    }

    public boolean firstTickOnGround() {
        return firstTickOnGround;
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
        pausedAtNanos = 0L;

        simTickDumps.clear();
        if (DebugFlags.DUMP_TICK_STATE) {
            DebugFlags.simTickSink = simTickDumps;
            try {
                runner.simulate(inputData);
            } finally {
                DebugFlags.simTickSink = null;
            }
        }
        firstTickOnGround = runner.firstTickOnGround();
        bridge.teleport(runner.getStartPosition(), runner.getStartVelocity(), runner.getStartYaw(), firstTickOnGround);
        // Drop any user-held key so the warmup runs with an empty InputRow like the simulator does.
        bridge.releaseAllKeys();
        nextTick = 0;
        warmupRemaining = WARMUP_TICKS;
        prevTickYaw = runner.getStartYaw();
        currentTickYaw = runner.getStartYaw();
        displayTargetYaw = runner.getStartYaw();
        displayedYaw = runner.getStartYaw();
        tickEndNanos = 0L;
        lastFrameNanos = 0L;
        InputRow firstRow = inputData.get(0);
        int firstSpeedAmp = firstRow.getSpeedAmplifier();
        int firstJumpAmp = firstRow.getJumpBoostAmplifier();
        bridge.applyEffects(firstSpeedAmp, firstJumpAmp);
        lastSpeedAmplifier = firstSpeedAmp;
        lastJumpBoostAmplifier = firstJumpAmp;
        running = true;
    }

    public void stop() {
        if (!running) return;
        running = false;
        warmupRemaining = 0;
        tickEndNanos = 0L;
        lastFrameNanos = 0L;
        pausedAtNanos = 0L;
        if (bridge != null) {
            bridge.releaseAllKeys();
            bridge.applyEffects(0, 0);
        }
        lastSpeedAmplifier = 0;
        lastJumpBoostAmplifier = 0;
    }

    /** Loader calls each START_CLIENT_TICK. */
    public void tick() {
        if (!running || bridge == null) return;
        if (bridge.isGamePaused()) {
            // Freeze, don't consume: the paused world ran no physics for this client tick. Keys are
            // dropped once so nothing is held into the pause screen; the next live tick re-applies
            // its row's full key state anyway.
            if (pausedAtNanos == 0L) {
                pausedAtNanos = System.nanoTime();
                bridge.releaseAllKeys();
            }
            return;
        }
        if (pausedAtNanos != 0L) {
            long pausedFor = System.nanoTime() - pausedAtNanos;
            if (tickEndNanos != 0L) tickEndNanos += pausedFor;
            if (lastFrameNanos != 0L) lastFrameNanos += pausedFor;
            pausedAtNanos = 0L;
        }
        if (nextTick >= inputData.size()) {
            // Stop only once the visual has caught up to the final yaw and a tick
            // window has elapsed; a low cap can keep the ease running past the final input.
            boolean caughtUp = displayedYaw == displayTargetYaw;
            boolean windowElapsed = tickEndNanos != 0L && System.nanoTime() - tickEndNanos >= TICK_NANOS;
            if (caughtUp && windowElapsed) {
                stop();
            } else {
                bridge.releaseAllKeys();
            }
            return;
        }


        if (warmupRemaining > 0) {
            bridge.releaseAllKeys();
            warmupRemaining--;
            return;
        }

        InputRow row = inputData.get(nextTick);
        for (InputRow.Key key : InputRow.Key.values()) {
            bridge.setKey(key, row.isKeyActive(key));
        }
        int speedAmp = row.getSpeedAmplifier();
        int jumpAmp = row.getJumpBoostAmplifier();
        if (speedAmp != lastSpeedAmplifier || jumpAmp != lastJumpBoostAmplifier) {
            bridge.applyEffects(speedAmp, jumpAmp);
            lastSpeedAmplifier = speedAmp;
            lastJumpBoostAmplifier = jumpAmp;
        }
        Float yaw = row.getYaw();
        prevTickYaw = displayTargetYaw;
        if (yaw != null) {
            if (row.isYawLocked()) {
                // Physics takes the raw target like setYawAbsolute; only the visible head
                // turns the short way (e.g. -170 -> 135 turns -55, not +305).
                currentTickYaw = yaw;
                displayTargetYaw = prevTickYaw + shortestDelta(prevTickYaw, yaw);
            } else if (yaw != 0f) {
                currentTickYaw += yaw;
                displayTargetYaw += yaw;
            }
        }
        bridge.setYaw(currentTickYaw);
        tickEndNanos = System.nanoTime();
        nextTick++;
    }

    /** Signed degrees from -> to taken the short way round, in [-180, 180]. */
    private static float shortestDelta(float from, float to) {
        float d = to - from;
        while (d > 180.0f) d -= 360.0f;
        while (d < -180.0f) d += 360.0f;
        return d;
    }

    /** Loader calls after MC's physics tick so the snap value never reaches a render. */
    public void postTick() {
        if (!running || bridge == null) return;
        bridge.setYaw(displayedYaw);
        if (DebugFlags.DUMP_TICK_STATE) {
            // Negative index = warmup tick, so state going into tick 0 is visible.
            int t = nextTick - 1 - warmupRemaining;
            if (t >= 0 && t < simTickDumps.size()) {
                System.out.println(simTickDumps.get(t));
            }
            bridge.dumpPlayerState(t);
        }
    }

    /** Loader calls each render frame. */
    public void renderFrame() {
        if (!running || bridge == null) return;
        if (tickEndNanos == 0L) return;
        // The menu still renders frames while paused; the camera ease must not progress through them.
        if (pausedAtNanos != 0L || bridge.isGamePaused()) return;

        long now = System.nanoTime();
        float dt;
        if (lastFrameNanos == 0L) {
            dt = 0f;
        } else {
            dt = (now - lastFrameNanos) / 1_000_000_000f;
            if (dt > MAX_FRAME_DT_SECONDS) dt = MAX_FRAME_DT_SECONDS;
        }
        lastFrameNanos = now;

        long elapsed = now - tickEndNanos;
        float partial = elapsed / (float) TICK_NANOS;
        if (partial < 0f) partial = 0f;
        if (partial > 1f) partial = 1f;
        float idealYaw = prevTickYaw + (displayTargetYaw - prevTickYaw) * partial;

        float delta = idealYaw - displayedYaw;
        float maxStep = settings.yawFlickSpeed * dt;
        if (Math.abs(delta) <= maxStep) {
            displayedYaw = idealYaw;
        } else {
            displayedYaw += Math.signum(delta) * maxStep;
        }
        bridge.setYaw(displayedYaw);
    }
}
