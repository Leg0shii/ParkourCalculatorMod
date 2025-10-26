package de.legoshi.ui;

public record InputTick(boolean w, boolean a, boolean s, boolean d, boolean j, boolean p, boolean n, float f) {
    public InputTick() {
        this(false, false, false, false, false, false, false, 0.0F);
    }
}
