package de.legoshi.parkourcalc.core.ui;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public class InputRow {

    private static int nextId = 0;

    public static final int MAX_AMPLIFIER = 9;

    public static final int MAX_HOTBAR_SLOT = 9;

    private final int id;
    private final Set<Key> activeKeys = EnumSet.noneOf(Key.class);
    private Float yaw;
    private boolean yawLocked;
    private Float pitch;
    private boolean pitchLocked;
    private int speedAmplifier;
    private int jumpBoostAmplifier;
    private int hotbarSlot;
    private boolean teleportEnabled;
    private double teleportX;
    private double teleportY;
    private double teleportZ;
    private int modCount;

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

    public int getModCount() {
        return modCount;
    }

    public boolean isKeyActive(Key key) {
        return activeKeys.contains(key);
    }

    public void setKeyActive(Key key, boolean active) {
        boolean changed = active ? activeKeys.add(key) : activeKeys.remove(key);
        if (changed) modCount++;
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
        if (!Objects.equals(this.yaw, yaw)) modCount++;
        this.yaw = yaw;
    }

    public boolean isYawLocked() {
        return yawLocked;
    }

    public void setYawLocked(boolean yawLocked) {
        if (this.yawLocked != yawLocked) modCount++;
        this.yawLocked = yawLocked;
    }

    public Float getPitch() {
        return pitch;
    }

    public void setPitch(Float pitch) {
        if (!Objects.equals(this.pitch, pitch)) modCount++;
        this.pitch = pitch;
    }

    public boolean isPitchLocked() {
        return pitchLocked;
    }

    public void setPitchLocked(boolean pitchLocked) {
        if (this.pitchLocked != pitchLocked) modCount++;
        this.pitchLocked = pitchLocked;
    }

    public int getSpeedAmplifier() {
        return speedAmplifier;
    }

    public void setSpeedAmplifier(int amplifier) {
        int clamped = clampAmplifier(amplifier);
        if (this.speedAmplifier != clamped) modCount++;
        this.speedAmplifier = clamped;
    }

    public int getJumpBoostAmplifier() {
        return jumpBoostAmplifier;
    }

    public void setJumpBoostAmplifier(int amplifier) {
        int clamped = clampAmplifier(amplifier);
        if (this.jumpBoostAmplifier != clamped) modCount++;
        this.jumpBoostAmplifier = clamped;
    }

    public int getHotbarSlot() {
        return hotbarSlot;
    }

    public void setHotbarSlot(int slot) {
        int clamped = clampHotbarSlot(slot);
        if (this.hotbarSlot != clamped) modCount++;
        this.hotbarSlot = clamped;
    }

    public boolean isTeleportEnabled() {
        return teleportEnabled;
    }

    public void setTeleportEnabled(boolean enabled) {
        if (this.teleportEnabled != enabled) modCount++;
        this.teleportEnabled = enabled;
    }

    public double getTeleportX() {
        return teleportX;
    }

    public double getTeleportY() {
        return teleportY;
    }

    public double getTeleportZ() {
        return teleportZ;
    }

    public void setTeleportDestination(double x, double y, double z) {
        if (this.teleportX != x || this.teleportY != y || this.teleportZ != z) modCount++;
        this.teleportX = x;
        this.teleportY = y;
        this.teleportZ = z;
    }

    private static int clampAmplifier(int amplifier) {
        if (amplifier < 0) return 0;
        if (amplifier > MAX_AMPLIFIER) return MAX_AMPLIFIER;
        return amplifier;
    }

    private static int clampHotbarSlot(int slot) {
        if (slot < 0) return 0;
        if (slot > MAX_HOTBAR_SLOT) return MAX_HOTBAR_SLOT;
        return slot;
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
        copy.hotbarSlot = this.hotbarSlot;
        copy.teleportEnabled = this.teleportEnabled;
        copy.teleportX = this.teleportX;
        copy.teleportY = this.teleportY;
        copy.teleportZ = this.teleportZ;
        return copy;
    }
}