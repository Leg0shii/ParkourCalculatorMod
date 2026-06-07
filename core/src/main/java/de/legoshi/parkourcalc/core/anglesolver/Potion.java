package de.legoshi.parkourcalc.core.anglesolver;

/** Potion-effect pool. The amplifier level is held separately on {@link PotionDose}. */
public enum Potion {
    SPEED("Speed"),
    JUMP_BOOST("Jump boost"),
    SLOWNESS("Slowness");

    public final String label;

    Potion(String label) {
        this.label = label;
    }
}
