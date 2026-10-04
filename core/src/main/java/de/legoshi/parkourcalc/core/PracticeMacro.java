package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.ui.Settings;

public final class PracticeMacro {

    public static final int OFF = 0;
    public static final int INPUTS = 1;
    public static final int TURN = 2;
    public static final String LABEL_INPUTS = "Replay (inputs)";
    public static final String LABEL_TURN = "Replay (turn)";
    private static final int WAIT_TIMEOUT_TICKS = 200;
    private static final int TAIL_TIMEOUT_TICKS = 100;

    private final TurnProfileController profile;
    private final AttemptTracker tracker;
    private final Settings settings;
    private PlaybackBridge bridge;
    private long armedAtNanos;
    private boolean active;
    private int tick;
    private int mode;

    public PracticeMacro(TurnProfileController profile, AttemptTracker tracker, Settings settings) {
        this.profile = profile;
        this.tracker = tracker;
        this.settings = settings;
    }

    public void setBridge(PlaybackBridge bridge) {
        this.bridge = bridge;
    }

    public int mode() {
        return settings.onejumpMacroMode;
    }

    public String label() {
        switch (mode()) {
            case INPUTS: return LABEL_INPUTS;
            case TURN: return LABEL_TURN;
            default: return null;
        }
    }

    public boolean isRunning() {
        return active;
    }

    public void onReset() {
        stop();
        armedAtNanos = mode() == OFF ? 0L : System.nanoTime();
    }

    public void tick() {
        int wanted = mode();
        if (wanted == OFF || bridge == null) {
            stop();
            armedAtNanos = 0L;
            return;
        }
        TurnProfileController.Current cur = profile.current();
        if (cur == null || cur.n == 0) {
            stop();
            return;
        }
        if (!active) {
            if (armedAtNanos == 0L) return;
            long delay = Math.max(0L, settings.onejumpMacroDelayMs) * 1_000_000L;
            if (System.nanoTime() - armedAtNanos < delay) return;
            armedAtNanos = 0L;
            if (!bridge.beginReplica(wanted == TURN)) return;
            active = true;
            mode = wanted;
            tick = 0;
        }
        if (!bridge.replicaActive()) {
            stop();
            return;
        }
        if (mode == INPUTS) tickInputs(cur);
        else tickTurn(cur);
        tick++;
    }

    private void tickInputs(TurnProfileController.Current cur) {
        if (tick < cur.n) {
            applyKeys(cur.keys[tick]);
            return;
        }
        bridge.releaseAllKeys();
        if (tracker.live() == null || tick > cur.n + TAIL_TIMEOUT_TICKS) stop();
    }

    private void tickTurn(TurnProfileController.Current cur) {
        applyKeys(bridge.playerKeyMask());
        TurnAttempt live = tracker.live();
        int t;
        if (live != null) {
            t = Math.min(live.recorded, cur.n - 1);
        } else if (tracker.isArmed()) {
            if (tick > WAIT_TIMEOUT_TICKS) {
                stop();
                return;
            }
            t = Math.max(0, cur.firstJumpRow());
        } else {
            stop();
            return;
        }
        float yaw = (float) cur.facing[t];
        bridge.setYaw(yaw);
        bridge.setHeadYaw(yaw);
    }

    private void applyKeys(int keys) {
        for (int i = 0; i < TurnReference.KEYS.length; i++) {
            bridge.setKey(TurnReference.KEYS[i], (keys & TurnReference.BITS[i]) != 0);
        }
    }

    public void stop() {
        if (!active) return;
        active = false;
        if (bridge == null) return;
        bridge.releaseAllKeys();
        bridge.endReplica();
    }
}
