package de.legoshi.parkourcalc.core.multireplay;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ReplayTrack {

    public static final double SWING_TICKS = 6.0;

    public final String name;
    public final int argb;
    private final List<Vec3dCore> positions;
    private final float[] yaws;
    private final float[] pitches;
    private final boolean[] sneaking;
    private final boolean[] swings;
    private boolean visible = true;

    public ReplayTrack(String name, int argb, List<Vec3dCore> positions) {
        this(name, argb, positions, null, null, null, null);
    }

    public ReplayTrack(String name, int argb, List<Vec3dCore> positions, float[] yaws, float[] pitches) {
        this(name, argb, positions, yaws, pitches, null, null);
    }

    public ReplayTrack(String name, int argb, List<Vec3dCore> positions, float[] yaws, float[] pitches,
                       boolean[] sneaking, boolean[] swings) {
        this.name = name;
        this.argb = argb;
        this.positions = Collections.unmodifiableList(new ArrayList<Vec3dCore>(positions));
        int n = this.positions.size();
        this.yaws = copyInto(yaws, n);
        this.pitches = copyInto(pitches, n);
        this.sneaking = copyInto(sneaking, n);
        this.swings = copyInto(swings, n);
    }

    private static float[] copyInto(float[] src, int n) {
        float[] out = new float[n];
        if (src != null) System.arraycopy(src, 0, out, 0, Math.min(src.length, n));
        return out;
    }

    private static boolean[] copyInto(boolean[] src, int n) {
        boolean[] out = new boolean[n];
        if (src != null) System.arraycopy(src, 0, out, 0, Math.min(src.length, n));
        return out;
    }

    public int size() {
        return positions.size();
    }

    public int lastTick() {
        return Math.max(0, positions.size() - 1);
    }

    public Vec3dCore position(int tick) {
        return positions.get(tick);
    }

    public float yaw(int tick) {
        return yaws[tick];
    }

    public float pitch(int tick) {
        return pitches[tick];
    }

    public boolean sneaking(int tick) {
        return sneaking[tick];
    }

    public boolean swings(int tick) {
        return swings[tick];
    }

    public Vec3dCore positionAt(double time) {
        if (positions.isEmpty()) return Vec3dCore.ZERO;
        int k = clampTick(time);
        double frac = time - k;
        if (k >= lastTick() || frac <= 0) return positions.get(k);
        Vec3dCore a = positions.get(k);
        Vec3dCore b = positions.get(k + 1);
        return new Vec3dCore(a.x + (b.x - a.x) * frac, a.y + (b.y - a.y) * frac, a.z + (b.z - a.z) * frac);
    }

    public float yawAt(double time) {
        if (positions.isEmpty()) return 0f;
        int k = clampTick(time);
        double frac = time - k;
        if (k >= lastTick() || frac <= 0) return yaws[k];
        float a = yaws[k];
        float delta = yaws[k + 1] - a;
        while (delta > 180f) delta -= 360f;
        while (delta < -180f) delta += 360f;
        return (float) (a + delta * frac);
    }

    public float pitchAt(double time) {
        if (positions.isEmpty()) return 0f;
        int k = clampTick(time);
        double frac = time - k;
        if (k >= lastTick() || frac <= 0) return pitches[k];
        return (float) (pitches[k] + (pitches[k + 1] - pitches[k]) * frac);
    }

    public boolean sneakingAt(double time) {
        return !positions.isEmpty() && sneaking[clampTick(time)];
    }

    public float swingProgressAt(double time) {
        if (positions.isEmpty()) return 0f;
        int k = clampTick(time);
        for (int c = k; c >= 0 && time - c < SWING_TICKS; c--) {
            if (swings[c]) return (float) ((time - c) / SWING_TICKS);
        }
        return 0f;
    }

    private int clampTick(double time) {
        if (time <= 0 || Double.isNaN(time)) return 0;
        return (int) Math.min(lastTick(), Math.floor(time));
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
