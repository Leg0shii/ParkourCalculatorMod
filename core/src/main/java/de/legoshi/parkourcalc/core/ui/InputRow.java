package de.legoshi.parkourcalc.core.ui;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public class InputRow {

    private static int nextId = 0;

    public static final int MAX_AMPLIFIER = MovementEffect.MAX_LEVEL;

    public static final int MAX_HOTBAR_SLOT = 9;

    public static final int ONEJUMP_FACE_OFF = 0;
    public static final int ONEJUMP_FACE_CHECK = 1;
    public static final int ONEJUMP_FACE_STILL = 2;

    private final int id;
    private final Set<Key> activeKeys = EnumSet.noneOf(Key.class);
    private Float yaw;
    private boolean yawLocked;
    private Float pitch;
    private boolean pitchLocked;
    private final int[] effectLevels = new int[MovementEffect.count()];
    private int hotbarSlot;
    private boolean teleportEnabled;
    private double teleportX;
    private double teleportY;
    private double teleportZ;
    private boolean onejumpKeys;
    private int onejumpFace;
    private boolean onejumpLand;
    private final Set<Key> onejumpOptional = EnumSet.noneOf(Key.class);
    private int modCount;

    // LEFT_CLICK / RIGHT_CLICK appended last to keep existing ordinals stable for old saves.
    public enum Key {
        W, A, S, D, SPRINT, SNEAK, JUMP, LEFT_CLICK, RIGHT_CLICK, CLOSE_INVENTORY
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
        return getEffectLevel(MovementEffect.SPEED);
    }

    public void setSpeedAmplifier(int amplifier) {
        setEffectLevel(MovementEffect.SPEED, amplifier);
    }

    public int getJumpBoostAmplifier() {
        return getEffectLevel(MovementEffect.JUMP_BOOST);
    }

    public void setJumpBoostAmplifier(int amplifier) {
        setEffectLevel(MovementEffect.JUMP_BOOST, amplifier);
    }

    public int getSlownessAmplifier() {
        return getEffectLevel(MovementEffect.SLOWNESS);
    }

    public void setSlownessAmplifier(int amplifier) {
        setEffectLevel(MovementEffect.SLOWNESS, amplifier);
    }

    public int getEffectLevel(MovementEffect effect) {
        return effectLevels[effect.ordinal()];
    }

    public void setEffectLevel(MovementEffect effect, int level) {
        int clamped = effect.clampLevel(level);
        if (effectLevels[effect.ordinal()] != clamped) modCount++;
        effectLevels[effect.ordinal()] = clamped;
    }

    public int[] getEffectLevels() {
        return effectLevels.clone();
    }

    public boolean hasEffects() {
        for (int level : effectLevels) {
            if (level > 0) return true;
        }
        return false;
    }

    public boolean sameEffects(InputRow other) {
        return Arrays.equals(effectLevels, other.effectLevels);
    }

    public void copyEffectsFrom(InputRow other) {
        for (MovementEffect e : MovementEffect.all()) setEffectLevel(e, other.getEffectLevel(e));
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

    public boolean isOnejumpKeys() {
        return onejumpKeys;
    }

    public void setOnejumpKeys(boolean check) {
        if (this.onejumpKeys != check) modCount++;
        this.onejumpKeys = check;
    }

    public int getOnejumpFace() {
        return onejumpFace;
    }

    public void setOnejumpFace(int face) {
        int clamped = face < 0 ? 0 : face > ONEJUMP_FACE_STILL ? ONEJUMP_FACE_STILL : face;
        if (this.onejumpFace != clamped) modCount++;
        this.onejumpFace = clamped;
    }

    public boolean isOnejumpLand() {
        return onejumpLand;
    }

    public void setOnejumpLand(boolean land) {
        if (this.onejumpLand != land) modCount++;
        this.onejumpLand = land;
    }

    public boolean isOnejumpOptional(Key key) {
        return onejumpOptional.contains(key);
    }

    public void setOnejumpOptional(Key key, boolean optional) {
        boolean changed = optional ? onejumpOptional.add(key) : onejumpOptional.remove(key);
        if (changed) modCount++;
    }

    public boolean hasOnejumpOptional() {
        return !onejumpOptional.isEmpty();
    }

    public boolean isOnejumpFlagged() {
        return onejumpKeys || onejumpFace != ONEJUMP_FACE_OFF;
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
        System.arraycopy(this.effectLevels, 0, copy.effectLevels, 0, this.effectLevels.length);
        copy.hotbarSlot = this.hotbarSlot;
        copy.teleportEnabled = this.teleportEnabled;
        copy.teleportX = this.teleportX;
        copy.teleportY = this.teleportY;
        copy.teleportZ = this.teleportZ;
        copy.onejumpKeys = this.onejumpKeys;
        copy.onejumpFace = this.onejumpFace;
        copy.onejumpLand = this.onejumpLand;
        copy.onejumpOptional.addAll(this.onejumpOptional);
        return copy;
    }
}