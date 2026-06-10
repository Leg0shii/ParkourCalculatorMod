package de.legoshi.parkourcalc.core.anglesolver;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Per-tick override of the solver's default state. A null field inherits the default.
 * Potion is expressed as a delta vs the default: {@code added} = doses added on this tick
 * (each carries its own amplifier), {@code removed} = removed on this tick (struck through).
 */
public final class StateOverride {

    private AngleSolverState.InputMode inputs;
    private Slipperiness slipperiness;
    private final List<PotionDose> added = new ArrayList<>();
    private final Set<Potion> removed = EnumSet.noneOf(Potion.class);

    public AngleSolverState.InputMode getInputs() {
        return inputs;
    }

    public void setInputs(AngleSolverState.InputMode inputs) {
        this.inputs = inputs;
    }

    public boolean overridesInputs() {
        return inputs != null;
    }

    public void clearInputs() {
        inputs = null;
    }

    public Slipperiness getSlipperiness() {
        return slipperiness;
    }

    public void setSlipperiness(Slipperiness slipperiness) {
        this.slipperiness = slipperiness;
    }

    public boolean overridesSlipperiness() {
        return slipperiness != null;
    }

    public void clearSlipperiness() {
        slipperiness = null;
    }


    public List<PotionDose> getAdded() {
        return added;
    }

    public Set<Potion> getRemoved() {
        return removed;
    }

    public boolean overridesPotion() {
        return !added.isEmpty() || !removed.isEmpty();
    }

    public boolean hasAdded(Potion p) {
        for (PotionDose d : added) if (d.potion == p) return true;
        return false;
    }

    public PotionDose findAdded(Potion p) {
        for (PotionDose d : added) if (d.potion == p) return d;
        return null;
    }

    public void removeAdded(Potion p) {
        added.removeIf(d -> d.potion == p);
    }

    public boolean isEmpty() {
        return !overridesInputs() && !overridesSlipperiness() && !overridesPotion();
    }

    /** Make this override an independent copy of {@code other} (doses deep-copied: they are mutable). */
    public void copyFrom(StateOverride other) {
        inputs = other.inputs;
        slipperiness = other.slipperiness;
        added.clear();
        for (PotionDose d : other.added) added.add(new PotionDose(d.potion, d.level));
        removed.clear();
        removed.addAll(other.removed);
    }
}
