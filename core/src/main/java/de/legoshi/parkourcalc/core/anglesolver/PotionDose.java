package de.legoshi.parkourcalc.core.anglesolver;

/** A potion effect paired with its amplifier level (1-255). */
public final class PotionDose {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 255;

    public Potion potion;
    public int level;

    public PotionDose(Potion potion, int level) {
        this.potion = potion;
        this.level = level;
    }
}
