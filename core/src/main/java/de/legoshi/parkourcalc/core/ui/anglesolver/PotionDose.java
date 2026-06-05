package de.legoshi.parkourcalc.core.ui.anglesolver;

/** A default-state potion effect paired with its amplifier level (1-10 for now). */
public final class PotionDose {

    public Potion potion;
    public int level;

    public PotionDose(Potion potion, int level) {
        this.potion = potion;
        this.level = level;
    }
}
