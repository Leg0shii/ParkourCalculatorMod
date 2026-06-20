package de.legoshi.parkourcalc.core.ui;

import java.util.EnumSet;
import java.util.Set;

public class InputRow {

    private static int nextId = 0;

    public static final int MAX_AMPLIFIER = 9;

    private final int id;
    private final Set<Key> activeKeys = EnumSet.noneOf(Key.class);
    private Float yaw;
    private boolean yawLocked;
    private Float pitch;
    private boolean pitchLocked;
    private int speedAmplifier;
    private int jumpBoostAmplifier;

    // LEFT_CLICK / RIGHT_CLICK appended last to keep existing ordinals stable for old saves.
    public enum Key {
        W, A, S, D, SPRINT, SNEAK, JUMP, LEFT_CLICK, RIGHT_CLICK
    }

    public InputRow() {
        this.id = nextId++;
    }

    public int getId() {
        return id;
    }

    public boolean isKeyActive(Key key) {
        return activeKeys.contains(key);
    }

    public void setKeyActive(Key key, boolean active) {
        if (active) {
            activeKeys.add(key);
        } else {
            activeKeys.remove(key);
        }
    }

    public void applyForce45(boolean strafe, int strafeSign) {
        setKeyActive(Key.W, true);
        setKeyActive(Key.SPRINT, true);
        setKeyActive(Key.A, strafe && strafeSign > 0);
        setKeyActive(Key.D, strafe && strafeSign < 0);
    }

    public Float getYaw() {
        return yaw;
    }

    public void setYaw(Float yaw) {
        this.yaw = yaw;
    }

    public boolean isYawLocked() {
        return yawLocked;
    }

    public void setYawLocked(boolean yawLocked) {
        this.yawLocked = yawLocked;
    }

    public Float getPitch() {
        return pitch;
    }

    public void setPitch(Float pitch) {
        this.pitch = pitch;
    }

    public boolean isPitchLocked() {
        return pitchLocked;
    }

    public void setPitchLocked(boolean pitchLocked) {
        this.pitchLocked = pitchLocked;
    }

    public int getSpeedAmplifier() {
        return speedAmplifier;
    }

    public void setSpeedAmplifier(int amplifier) {
        this.speedAmplifier = clampAmplifier(amplifier);
    }

    public int getJumpBoostAmplifier() {
        return jumpBoostAmplifier;
    }

    public void setJumpBoostAmplifier(int amplifier) {
        this.jumpBoostAmplifier = clampAmplifier(amplifier);
    }

    private static int clampAmplifier(int amplifier) {
        if (amplifier < 0) return 0;
        if (amplifier > MAX_AMPLIFIER) return MAX_AMPLIFIER;
        return amplifier;
    }

    public InputRow copy() {
        InputRow copy = new InputRow();
        copy.activeKeys.addAll(this.activeKeys);
        copy.yaw = this.yaw;
        copy.yawLocked = this.yawLocked;
        copy.pitch = this.pitch;
        copy.pitchLocked = this.pitchLocked;
        copy.speedAmplifier = this.speedAmplifier;
        copy.jumpBoostAmplifier = this.jumpBoostAmplifier;
        return copy;
    }
}