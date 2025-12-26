package de.legoshi.ui;

import java.util.EnumSet;
import java.util.Set;

public class InputRow {

    private static int nextId = 0;

    private final int id;
    private final Set<Key> activeKeys = EnumSet.noneOf(Key.class);
    private Float yaw;

    public enum Key {
        W, A, S, D, SPRINT, SNEAK, JUMP
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

    public Float getYaw() {
        return yaw;
    }

    public void setYaw(Float yaw) {
        this.yaw = yaw;
    }

    public InputRow copy() {
        InputRow copy = new InputRow();
        copy.activeKeys.addAll(this.activeKeys);
        copy.yaw = this.yaw;
        return copy;
    }
}