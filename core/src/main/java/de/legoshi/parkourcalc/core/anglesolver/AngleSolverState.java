package de.legoshi.parkourcalc.core.anglesolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Angle Solver's data model: whole-problem inputs, the default per-tick state, and
 * per-tick constraints / overrides keyed by 0-based tick index. This is the feature's own
 * state (the editable InputRow table is left untouched); sample data is seeded by the caller.
 */
public final class AngleSolverState {

    public enum Axis {
        X,
        Z
    }

    public enum Goal {
        MAX,
        MIN
    }

    public enum InputMode {
        KEEP("Keep"),
        FORCE_45("Force 45");

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
    private Effort effort = Effort.FAST;

    private InputMode defaultInputs = InputMode.FORCE_45;
    private Slipperiness defaultSlipperiness = Slipperiness.AIR;
    private final List<PotionDose> defaultPotions = new ArrayList<>();

    private final Map<Integer, TickConstraints> ticks = new LinkedHashMap<>();

    // Block selections drive the constraint generator. start/land are single; collisions are a list.
    // Picking is keybind-driven loader-side (a keypress captures the looked-at block), so there is no
    // in-UI arming state here anymore.
    private BlockSelection startBlock;
    private BlockSelection landBlock;
    private final List<BlockSelection> collisionBlocks = new ArrayList<>();

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

    // ---- row edits: tick-anchored data follows its rows (gh-89) ----------------

    /** Rows inserted at {@code index}: constraints/overrides and the start/landing flags at or past
     *  it slide down with their rows. */
    public void onRowsInserted(int index, int count) {
        if (count <= 0) return;
        remapTicks(t -> t >= index ? t + count : t);
        if (startTick >= index) startTick += count;
        if (landingTick >= index) landingTick += count;
    }

    /** Rows removed at {@code descendingIndices} (the shape InputData.removeRows consumes): their
     *  tick entries die with the rows, everything below slides up. The start/landing flags slide to
     *  the row that takes the removed index's place (clamped by the per-frame clampTicks). */
    public void onRowsRemoved(List<Integer> descendingIndices) {
        if (descendingIndices.isEmpty()) return;
        final List<Integer> asc = new ArrayList<>(descendingIndices);
        Collections.sort(asc);
        remapTicks(t -> Collections.binarySearch(asc, t) >= 0 ? -1 : t - countBelow(asc, t));
        startTick -= countBelow(asc, startTick);
        landingTick -= countBelow(asc, landingTick);
    }

    /** The row at {@code sourceIndex} was duplicated onto {@code sourceIndex + 1}: slide everything
     *  past the copy, then deep-copy the source tick's constraints and override onto it (gh-111). */
    public void onRowDuplicated(int sourceIndex) {
        onRowsInserted(sourceIndex + 1, 1);
        TickConstraints src = ticks.get(sourceIndex);
        if (src == null) return;
        TickConstraints dst = tickConstraints(sourceIndex + 1);
        for (Constraint c : src.getConstraints()) dst.getConstraints().add(c.copy());
        dst.getOverride().copyFrom(src.getOverride());
    }


    /** A row moved with InputData.moveRow's drop-line semantics: {@code to} is the gap index, a no-op
     *  when it neighbors {@code from}; otherwise list-remove at {@code from} + insert at the effective
     *  destination. Tick data rotates the same way. */
    public void onRowMoved(int from, int to) {
        if (from < 0 || to < 0 || from == to || from == to - 1) return;
        final int dest = from < to ? to - 1 : to;
        remapTicks(t -> mapMove(t, from, dest));
        startTick = mapMove(startTick, from, dest);
        landingTick = mapMove(landingTick, from, dest);
    }

    /** {@link #onRowMoved}'s index mapping for a single row-keyed value (same drop-line no-op semantics). */
    public static int mapRowMove(int t, int from, int to) {
        if (from < 0 || to < 0 || from == to || from == to - 1) return t;
        return mapMove(t, from, from < to ? to - 1 : to);
    }

    private static int mapMove(int t, int from, int dest) {
        if (t == from) return dest;
        if (from < dest) return (t > from && t <= dest) ? t - 1 : t;
        return (t >= dest && t < from) ? t + 1 : t;
    }

    private static int countBelow(List<Integer> ascending, int t) {
        int n = 0;
        for (int r : ascending) {
            if (r < t) n++;
            else break;
        }
        return n;
    }

    /** Rebuilds the tick map through {@code mapper} (a negative result drops the entry). Monotone
     *  shifts and rotations are bijective, so keys cannot collide. */
    private void remapTicks(java.util.function.IntUnaryOperator mapper) {
        Map<Integer, TickConstraints> next = new LinkedHashMap<>();
        for (Map.Entry<Integer, TickConstraints> e : ticks.entrySet()) {
            int t = mapper.applyAsInt(e.getKey());
            if (t >= 0) next.put(t, e.getValue());
        }
        ticks.clear();
        ticks.putAll(next);
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

    /** Drops every constraint on ticks in [fromTick, toTick] (state overrides are left intact). Used by the
     *  block generator, which authors the segment from scratch on each run. */
    public void clearConstraintsInRange(int fromTick, int toTick) {
        for (Map.Entry<Integer, TickConstraints> e : ticks.entrySet()) {
            int t = e.getKey();
            if (t >= fromTick && t <= toTick) e.getValue().getConstraints().clear();
        }
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

    // ---- block selections (drive BlockConstraintGenerator) --------------------

    public BlockSelection getStartBlock() {
        return startBlock;
    }

    public void setStartBlock(BlockSelection block) {
        this.startBlock = block;
    }

    public BlockSelection getLandBlock() {
        return landBlock;
    }

    public void setLandBlock(BlockSelection block) {
        this.landBlock = block;
    }

    public List<BlockSelection> getCollisionBlocks() {
        return collisionBlocks;
    }

    public void addCollisionBlock(BlockSelection block) {
        if (block != null) collisionBlocks.add(block);
    }

    public void removeCollisionBlock(int index) {
        if (index >= 0 && index < collisionBlocks.size()) collisionBlocks.remove(index);
    }

    /** Removes any selected block (start, land, or a collision) at these integer coords. Used by the
     *  loader's "remove looked-at block" keybind. */
    public void removeBlockAt(int x, int y, int z) {
        if (startBlock != null && startBlock.x == x && startBlock.y == y && startBlock.z == z) startBlock = null;
        if (landBlock != null && landBlock.x == x && landBlock.y == y && landBlock.z == z) landBlock = null;
        collisionBlocks.removeIf(b -> b.x == x && b.y == y && b.z == z);
    }

    public boolean hasAnyBlocks() {
        return startBlock != null || landBlock != null || !collisionBlocks.isEmpty();
    }

    /** The only block the solver requires is a Land to reach. The Start block is optional: when picked it
     *  pins the launch footprint (be inside it at the tick before the first jump); otherwise it is ignored. */
    public boolean hasRequiredBlocks() {
        return landBlock != null;
    }

    public void clearBlocks() {
        startBlock = null;
        landBlock = null;
        collisionBlocks.clear();
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
        effort = Effort.FAST;
        defaultInputs = InputMode.FORCE_45;
        defaultSlipperiness = Slipperiness.AIR;
        defaultPotions.clear();
        ticks.clear();
        startBlock = null;
        landBlock = null;
        collisionBlocks.clear();
        result = null;
    }

}
