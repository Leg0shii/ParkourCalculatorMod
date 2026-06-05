package de.legoshi.parkourcalc.core.ui.anglesolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    private int startTick;
    private int landingTick;
    private Axis axis = Axis.X;
    private Goal goal = Goal.MAX;

    private InputMode defaultInputs = InputMode.FORCE_45;
    private Slipperiness defaultSlipperiness = Slipperiness.AIR;
    private final List<PotionDose> defaultPotions = new ArrayList<>();

    private final Map<Integer, TickConstraints> ticks = new LinkedHashMap<>();

    private SolveResult result;

    // ---- problem inputs --------------------------------------------------------

    public int getStartTick() { return startTick; }
    public void setStartTick(int tick) { startTick = tick; }
    public int getLandingTick() { return landingTick; }
    public void setLandingTick(int tick) { landingTick = tick; }

    public Axis getAxis() { return axis; }
    public void setAxis(Axis axis) { this.axis = axis; }
    public Goal getGoal() { return goal; }
    public void setGoal(Goal goal) { this.goal = goal; }

    public boolean isStart(int tick) { return tick == startTick; }
    public boolean isLanding(int tick) { return tick == landingTick; }

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

    // ---- default state ---------------------------------------------------------

    public InputMode getDefaultInputs() { return defaultInputs; }
    public void setDefaultInputs(InputMode mode) { this.defaultInputs = mode; }
    public Slipperiness getDefaultSlipperiness() { return defaultSlipperiness; }
    public void setDefaultSlipperiness(Slipperiness slip) { this.defaultSlipperiness = slip; }
    public List<PotionDose> getDefaultPotions() { return defaultPotions; }

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

    // ---- per-tick constraints --------------------------------------------------

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

    public int constraintCount() {
        int n = 0;
        for (TickConstraints tc : ticks.values()) n += tc.getConstraints().size();
        return n;
    }

    public int constraintCount(int tick) {
        TickConstraints tc = ticks.get(tick);
        return tc == null ? 0 : tc.getConstraints().size();
    }

    public int ticksWithConstraints() {
        int n = 0;
        for (TickConstraints tc : ticks.values()) {
            if (!tc.getConstraints().isEmpty()) n++;
        }
        return n;
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

    // ---- solve (stubbed) -------------------------------------------------------

    public SolveResult getResult() { return result; }
    public void clearResult() { result = null; }

    /** Seeds the prototype's sample data (mock route, 0-based ticks). Production wires this to real state. */
    public void seedSample() {
        startTick = 3;
        landingTick = 9;
        axis = Axis.X;
        goal = Goal.MAX;
        defaultInputs = InputMode.FORCE_45;
        defaultSlipperiness = Slipperiness.AIR;
        defaultPotions.clear();
        ticks.clear();

        tickConstraints(3).getOverride().setInputs(InputMode.FORCE_45);

        TickConstraints t6 = tickConstraints(5);
        t6.getConstraints().add(Constraint.range(Constraint.Field.DX, 0.100, 0.300, false, false));
        t6.getOverride().setSlipperiness(Slipperiness.ICE);

        TickConstraints t8 = tickConstraints(7);
        t8.getConstraints().add(Constraint.scalar(Constraint.Field.Z, Constraint.Op.LT, -80.0));
        t8.getOverride().getAdded().add(new PotionDose(Potion.JUMP_BOOST, 1));

        TickConstraints t10 = tickConstraints(9);
        t10.getConstraints().add(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 124.5));
        t10.getConstraints().add(Constraint.scalar(Constraint.Field.Z, Constraint.Op.LT, -88.0));
        t10.getConstraints().add(Constraint.scalar(Constraint.Field.F, Constraint.Op.EQ, 0));
        t10.getConstraints().add(Constraint.range(Constraint.Field.DX, 0.200, 0.420, true, true));
        t10.getConstraints().add(Constraint.range(Constraint.Field.DZ, -0.10, 0.10, false, false));
    }

    /** Stubbed solve: fabricates a plausible result panel from the current constraints. */
    public void solve() {
        int total = constraintCount();
        SolveResult r = new SolveResult(true, total, total, startTick + 1, landingTick + 1);
        int idx = 0;
        for (Map.Entry<Integer, TickConstraints> e : ticks.entrySet()) {
            int tick = e.getKey();
            for (Constraint c : e.getValue().getConstraints()) {
                r.getOutcomes().add(outcome(tick, c, idx++));
            }
        }
        int span = Math.max(startTick, landingTick);
        int from = Math.min(startTick, landingTick);
        for (int t = from; t <= span; t++) {
            double yaw = -134.217728 + (t - from) * 7.314159;
            r.getYaws().add(new SolveResult.YawEntry(t + 1, yaw));
        }
        this.result = r;
    }

    private static SolveResult.Outcome outcome(int tick, Constraint c, int idx) {
        double slack = 0.105 + 0.043 * (idx % 5);
        String field = c.getField().label;
        String tickLabel = "T" + (tick + 1);
        if (c.isRange()) {
            double mid = (c.getLo() + c.getHi()) * 0.5;
            return new SolveResult.Outcome(field, tickLabel, ConstraintText.chip(c), fmt(mid), "");
        }
        double v = c.getValue();
        double found;
        switch (c.getOp()) {
            case LT:
            case LE:
                found = v - slack;
                break;
            case EQ:
                found = v;
                slack = 0;
                break;
            default: // GT, GE
                found = v + slack;
                break;
        }
        String relation = c.getOp().glyph + " " + ConstraintText.num(v);
        return new SolveResult.Outcome(field, tickLabel, relation, fmt(found), "+" + fmt(slack));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }
}
