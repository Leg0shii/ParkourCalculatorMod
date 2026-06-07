package de.legoshi.parkourcalc.core.anglesolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Angle Solver's data model: whole-problem inputs, the default per-tick state, and
 * per-tick constraints / overrides keyed by 0-based tick index. This is the feature's own
 * state (the editable InputRow table is left untouched); sample data is seeded by the caller.
 */
public final class AngleSolverState {

    public enum Axis { X, Z }

    public enum Goal { MAX, MIN }

    public enum InputMode {
        KEEP("Keep"), FORCE_45("Force 45");

        public final String label;

        InputMode(String label) {
            this.label = label;
        }
    }

    /** Solve effort: trades wall-clock for the last micrometers of objective. FAST is ~100ms but uses a
     *  smaller global search, so a hard jump can occasionally miss a feasible solution; bump up if so. */
    public enum Effort {
        FAST("Fast", "~100ms"),
        BALANCED("Balanced", "~250ms"),
        THOROUGH("Thorough", "~2-3s");

        public final String label;
        public final String hint;

        Effort(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }

    private int startTick;
    private int landingTick;
    private Axis axis = Axis.X;
    private Goal goal = Goal.MAX;
    private Effort effort = Effort.BALANCED;

    private InputMode defaultInputs = InputMode.FORCE_45;
    private Slipperiness defaultSlipperiness = Slipperiness.AIR;
    private final List<PotionDose> defaultPotions = new ArrayList<>();

    private final Map<Integer, TickConstraints> ticks = new LinkedHashMap<>();

    private SolveResult result;

    public int getStartTick() {
        return startTick;
    }

    public void setStartTick(int tick) {
        startTick = tick;
    }

    public int getLandingTick() {
        return landingTick;
    }

    public void setLandingTick(int tick) {
        landingTick = tick;
    }

    public Axis getAxis() {
        return axis;
    }

    public void setAxis(Axis axis) {
        this.axis = axis;
    }

    public Goal getGoal() {
        return goal;
    }

    public void setGoal(Goal goal) {
        this.goal = goal;
    }

    public Effort getEffort() {
        return effort;
    }

    public void setEffort(Effort effort) {
        this.effort = effort;
    }

    public boolean isStart(int tick) {
        return tick == startTick;
    }

    public boolean isLanding(int tick) {
        return tick == landingTick;
    }

    /** Keep the start/landing indices inside the current route. */
    public void clampTicks(int rowCount) {
        if (rowCount <= 0) return;
        startTick = clamp(startTick, rowCount);
        landingTick = clamp(landingTick, rowCount);
    }

    private static int clamp(int tick, int rowCount) {
        if (tick < 0) return 0;
        if (tick > rowCount - 1) return rowCount - 1;
        return tick;
    }

    public InputMode getDefaultInputs() {
        return defaultInputs;
    }

    public void setDefaultInputs(InputMode mode) {
        this.defaultInputs = mode;
    }

    public Slipperiness getDefaultSlipperiness() {
        return defaultSlipperiness;
    }

    public void setDefaultSlipperiness(Slipperiness slip) {
        this.defaultSlipperiness = slip;
    }

    public List<PotionDose> getDefaultPotions() {
        return defaultPotions;
    }

    public boolean hasDefaultPotion(Potion p) {
        for (PotionDose d : defaultPotions) if (d.potion == p) return true;
        return false;
    }

    public Potion nextUnusedDefaultPotion() {
        for (Potion p : Potion.values()) if (!hasDefaultPotion(p)) return p;
        return null;
    }

    public void addNextDefaultPotion() {
        Potion next = nextUnusedDefaultPotion();
        if (next != null) defaultPotions.add(new PotionDose(next, 1));
    }

    public void removeDefaultPotion(int index) {
        if (index >= 0 && index < defaultPotions.size()) defaultPotions.remove(index);
    }

    /** Potions selectable in row {@code index}: those not already used by another row (the row's own current effect stays available). */
    public List<Potion> availableDefaultPotions(int index) {
        List<Potion> out = new ArrayList<>();
        for (Potion p : Potion.values()) {
            boolean usedByOther = false;
            for (int i = 0; i < defaultPotions.size(); i++) {
                if (i != index && defaultPotions.get(i).potion == p) { usedByOther = true; break; }
            }
            if (!usedByOther) out.add(p);
        }
        return out;
    }

    /** Clears any per-tick override facet that now matches the default state, so changing a default drops the overrides it makes redundant. */
    public void pruneRedundantOverrides() {
        for (TickConstraints tc : ticks.values()) {
            StateOverride ov = tc.getOverride();
            if (ov.overridesInputs() && ov.getInputs() == defaultInputs) ov.clearInputs();
            if (ov.overridesSlipperiness() && ov.getSlipperiness() == defaultSlipperiness) ov.clearSlipperiness();
            ov.getAdded().removeIf(this::isDefaultDose);
            ov.getRemoved().removeIf(p -> !hasDefaultPotion(p));
        }
    }

    private boolean isDefaultDose(PotionDose d) {
        for (PotionDose def : defaultPotions) {
            if (def.potion == d.potion && def.level == d.level) return true;
        }
        return false;
    }

    public TickConstraints tickConstraints(int tick) {
        TickConstraints tc = ticks.get(tick);
        if (tc == null) {
            tc = new TickConstraints();
            ticks.put(tick, tc);
        }
        return tc;
    }

    public TickConstraints tickConstraintsOrNull(int tick) {
        return ticks.get(tick);
    }

    /** Tick indices that currently hold constraints or an override, in insertion order. */
    public List<Integer> populatedTicks() {
        return new ArrayList<>(ticks.keySet());
    }

    public void addConstraint(int tick) {
        tickConstraints(tick).getConstraints().add(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 0.0));
    }

    public void deleteConstraint(int tick, int index) {
        TickConstraints tc = ticks.get(tick);
        if (tc == null) return;
        List<Constraint> list = tc.getConstraints();
        if (index >= 0 && index < list.size()) list.remove(index);
    }

    public void moveConstraint(int fromTick, int index, int toTick) {
        Constraint c = removeAt(fromTick, index);
        if (c != null) tickConstraints(toTick).getConstraints().add(c);
    }

    public void copyConstraint(int fromTick, int index, int toTick) {
        TickConstraints src = ticks.get(fromTick);
        if (src == null) return;
        List<Constraint> list = src.getConstraints();
        if (index < 0 || index >= list.size()) return;
        tickConstraints(toTick).getConstraints().add(list.get(index).copy());
    }

    public void duplicateConstraint(int tick, int index) {
        TickConstraints tc = ticks.get(tick);
        if (tc == null) return;
        List<Constraint> list = tc.getConstraints();
        if (index >= 0 && index < list.size()) list.add(list.get(index).copy());
    }

    private Constraint removeAt(int tick, int index) {
        TickConstraints tc = ticks.get(tick);
        if (tc == null) return null;
        List<Constraint> list = tc.getConstraints();
        if (index < 0 || index >= list.size()) return null;
        return list.remove(index);
    }

    // Solving lives in AngleSolverEngine; the window's Solve button drives it and calls setResult.

    public SolveResult getResult() {
        return result;
    }

    public void clearResult() {
        result = null;
    }

    public void setResult(SolveResult result) {
        this.result = result;
    }

    /** Wipes all state back to construction defaults; used before loading a saved problem. */
    public void reset() {
        startTick = 0;
        landingTick = 0;
        axis = Axis.X;
        goal = Goal.MAX;
        effort = Effort.BALANCED;
        defaultInputs = InputMode.FORCE_45;
        defaultSlipperiness = Slipperiness.AIR;
        defaultPotions.clear();
        ticks.clear();
        result = null;
    }

}
