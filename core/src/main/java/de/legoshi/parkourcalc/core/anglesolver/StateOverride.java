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

    public static final int MAX_SOULSAND_CELLS = 8;

    private AngleSolverState.InputMode inputs;
    private AngleSolverState.SprintMode sprint;
    private Slipperiness slipperiness;
    private Medium medium;
    private int soulsandCells = 1;
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

    public AngleSolverState.SprintMode getSprint() {
        return sprint;
    }

    public void setSprint(AngleSolverState.SprintMode sprint) {
        this.sprint = sprint;
    }

    public boolean overridesSprint() {
        return sprint != null;
    }

    public void clearSprint() {
        sprint = null;
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

    public Medium getMedium() {
        return medium;
    }

    public void setMedium(Medium medium) {
        this.medium = medium;
        if (medium != Medium.SOULSAND) soulsandCells = 1;
    }

    public boolean overridesMedium() {
        return medium != null;
    }

    public void clearMedium() {
        medium = null;
        soulsandCells = 1;
    }

    public int getSoulsandCells() {
        return soulsandCells;
    }

    public void setSoulsandCells(int cells) {
        soulsandCells = Math.max(1, Math.min(MAX_SOULSAND_CELLS, cells));
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
        return !overridesInputs() && !overridesSprint() && !overridesSlipperiness() && !overridesMedium() && !overridesPotion();
    }

    /** Make this override an independent copy of {@code other} (doses deep-copied: they are mutable). */
    public void copyFrom(StateOverride other) {
        inputs = other.inputs;
        sprint = other.sprint;
        slipperiness = other.slipperiness;
        medium = other.medium;
        soulsandCells = other.soulsandCells;
        added.clear();
        for (PotionDose d : other.added) added.add(new PotionDose(d.potion, d.level));
        removed.clear();
        removed.addAll(other.removed);
    }
}
