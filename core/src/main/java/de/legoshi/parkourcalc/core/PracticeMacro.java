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
    private int speedAmp = -1;
    private int jumpAmp = -1;

    public PracticeMacro(TurnProfileController profile, AttemptTracker tracker, Settings settings) {
        this.profile = profile;
        this.tracker = tracker;
        this.settings = settings;
    }

    public void setBridge(PlaybackBridge bridge) {
        this.bridge = bridge;
    }

    private int mode() {
        return settings.onejumpMacroMode;
    }

    public int activeMode() {
        return active ? mode : OFF;
    }

    public String label() {
        if (!active) return null;
        return mode == TURN ? LABEL_TURN : LABEL_INPUTS;
    }

    public void onReset() {
        stop();
        armedAtNanos = mode() == OFF ? 0L : System.nanoTime();
    }

    public void tick() {
        int wanted = mode();
        if (wanted == OFF || bridge == null) {
            stop();
            return;
        }
        TurnProfileController.Current cur = profile.current();
        if (cur == null || cur.n == 0) {
            stop();
            armedAtNanos = 0L;
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
        applyEffects(cur, mode == INPUTS ? tick - cur.leadKeys.length : referenceTick(cur));
        if (mode == INPUTS) tickInputs(cur);
        else tickTurn(cur);
        tick++;
    }

    private void tickInputs(TurnProfileController.Current cur) {
        int lead = cur.leadKeys.length;
        if (tick < lead) {
            applyKeys(cur.leadKeys[tick]);
            return;
        }
        if (tick < lead + cur.n) {
            applyKeys(cur.keys[tick - lead]);
            return;
        }
        bridge.releaseAllKeys();
        if (tracker.live() == null || tick > lead + cur.n + TAIL_TIMEOUT_TICKS) stop();
    }

    private void tickTurn(TurnProfileController.Current cur) {
        applyKeys(bridge.playerKeyMask());
        int t = referenceTick(cur);
        if (t < 0) {
            stop();
            return;
        }
        float yaw = (float) cur.facing[t];
        bridge.setYaw(yaw);
        bridge.setHeadYaw(yaw);
    }

    private int referenceTick(TurnProfileController.Current cur) {
        TurnAttempt live = tracker.live();
        if (live != null) return Math.min(live.recorded, cur.n - 1);
        if (tracker.isArmed() && tick <= WAIT_TIMEOUT_TICKS) return 0;
        return -1;
    }

    private void applyEffects(TurnProfileController.Current cur, int t) {
        int k = Math.max(0, Math.min(cur.n - 1, t));
        int speed = cur.speedAmp[k];
        int jump = cur.jumpAmp[k];
        if (speed == speedAmp && jump == jumpAmp) return;
        speedAmp = speed;
        jumpAmp = jump;
        bridge.applyEffects(speed, jump);
    }

    private void applyKeys(int keys) {
        for (int i = 0; i < TurnReference.KEYS.length; i++) {
            bridge.setKey(TurnReference.KEYS[i], (keys & TurnReference.BITS[i]) != 0);
        }
    }

    public void stop() {
        armedAtNanos = 0L;
        if (!active) return;
        active = false;
        if (bridge == null) return;
        bridge.releaseAllKeys();
        if (speedAmp > 0 || jumpAmp > 0) bridge.applyEffects(0, 0);
        speedAmp = -1;
        jumpAmp = -1;
        bridge.endReplica();
    }
}
