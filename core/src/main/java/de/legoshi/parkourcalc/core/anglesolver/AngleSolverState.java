package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;

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

    /** Where the solve's per-tick sprint flag comes from: assumed on, or sampled from the recorded path. */
    public enum SprintMode {
        ALWAYS("Always"),
        DERIVE("Derive");

        public final String label;

        SprintMode(String label) {
            this.label = label;
        }
    }

    public enum InputMode {
        KEEP("Keep"),
        FORCE_45("Force 45");

        public final String label;

        InputMode(String label) {
            this.label = label;
        }
    }

    /** Solve effort. FAST returns the first byte-exact feasible solution (lowest latency). THOROUGH
     *  ("Optimize" in the UI; the constant keeps its name so old saves load) spends a time budget on the
     *  best objective it can find. CUSTOM exposes every knob. */
    public enum Effort {
        FAST("Fast", "First feasible solution"),
        THOROUGH("Optimize", "Best result within the time budget"),
        CUSTOM("Custom", "User graph preset");

        public final String label;
        public final String hint;

        Effort(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }

    public static final int MIN_TIME_BUDGET = 0;
    public static final int MAX_TIME_BUDGET = 600;
    public static final int DEFAULT_TIME_BUDGET = 0;
    public static final int MIN_OPTIMIZE_SECONDS = 1;
    public static final int MAX_OPTIMIZE_SECONDS = 600;
    public static final int DEFAULT_OPTIMIZE_SECONDS = 10;
    public static final int MIN_WINDOW = 6;
    public static final int MAX_WINDOW = 14;
    public static final int DEFAULT_WINDOW = 10;
    public static final int MIN_COMMIT = 1;
    public static final int DEFAULT_COMMIT = 3;

    public static final class SolveBudget {
        private int timeBudgetSeconds = DEFAULT_TIME_BUDGET;
        private int window = DEFAULT_WINDOW;
        private int commit = DEFAULT_COMMIT;
        private boolean useWindowSolver = true;
        private boolean ilsExhaustive = false;

        public int getTimeBudgetSeconds() { return timeBudgetSeconds; }
        public void setTimeBudgetSeconds(int v) { timeBudgetSeconds = clampInt(v, MIN_TIME_BUDGET, MAX_TIME_BUDGET); }

        public int getWindow() { return window; }
        public void setWindow(int v) {
            window = clampInt(v, MIN_WINDOW, MAX_WINDOW);
            if (commit > window - 1) commit = window - 1;
        }

        public int getCommit() { return commit; }
        public void setCommit(int v) { commit = clampInt(v, MIN_COMMIT, Math.max(MIN_COMMIT, window - 1)); }

        public boolean getUseWindowSolver() { return useWindowSolver; }
        public void setUseWindowSolver(boolean v) { useWindowSolver = v; }

        public boolean isIlsExhaustive() { return ilsExhaustive; }
        public void setIlsExhaustive(boolean v) { ilsExhaustive = v; }

        public void resetToDefaults() {
            timeBudgetSeconds = DEFAULT_TIME_BUDGET;
            window = DEFAULT_WINDOW;
            commit = DEFAULT_COMMIT;
            useWindowSolver = true;
            ilsExhaustive = false;
        }
    }

    private static int clampInt(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private int startTick;
    private int landingTick;
    private Axis axis = Axis.X;
    private Goal goal = Goal.MAX;
    private Effort effort = Effort.FAST;
    private boolean stopOnFeasible;
    private boolean legalMode;
    private int optimizeSeconds = DEFAULT_OPTIMIZE_SECONDS;
    public static final double TASER_SMOOTH_LAMBDA = 1.0e-2;

    private double smoothLambda;
    private final SolveBudget solveBudget = new SolveBudget();
    private String graphPresetName;
    private SolverGraph customGraph;

    private InputMode defaultInputs = InputMode.FORCE_45;
    private SprintMode defaultSprint = SprintMode.ALWAYS;
    private Slipperiness defaultSlipperiness = Slipperiness.AIR;
    private final List<PotionDose> defaultPotions = new ArrayList<>();

    private final Map<Integer, TickConstraints> ticks = new LinkedHashMap<>();

    private final List<BlockSelection> momentumBlocks = new ArrayList<>();
    private final List<BlockSelection> collisionBlocks = new ArrayList<>();
    private final List<BlockSelection> landBlocks = new ArrayList<>();

    /** Why the resimmed path left the solved path; picks the explanation tooltip in the result panel. */
    public enum DeviationKind { WALL, SNEAK, OTHER }

    private SolveResult result;
    private String applyDeviation;
    private DeviationKind applyDeviationKind;
    private int applyDeviationTick = -1;

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

    public boolean isStopOnFeasible() {
        return stopOnFeasible;
    }

    public void setStopOnFeasible(boolean stopOnFeasible) {
        this.stopOnFeasible = stopOnFeasible;
    }

    public boolean isLegalMode() {
        return legalMode;
    }

    public void setLegalMode(boolean legalMode) {
        this.legalMode = legalMode;
    }

    public int getOptimizeSeconds() {
        return optimizeSeconds;
    }

    public void setOptimizeSeconds(int v) {
        optimizeSeconds = clampInt(v, MIN_OPTIMIZE_SECONDS, MAX_OPTIMIZE_SECONDS);
    }

    public double getSmoothLambda() {
        return smoothLambda;
    }

    public void setSmoothLambda(double v) {
        smoothLambda = Math.max(0.0, v);
    }

    public SolveBudget getSolveBudget() {
        return solveBudget;
    }

    public String getGraphPresetName() {
        return graphPresetName;
    }

    public void setGraphPresetName(String name) {
        this.graphPresetName = name;
    }

    public SolverGraph getCustomGraph() {
        return customGraph;
    }

    public void setCustomGraph(SolverGraph graph) {
        this.customGraph = graph;
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

    public SprintMode getDefaultSprint() {
        return defaultSprint;
    }

    public void setDefaultSprint(SprintMode mode) {
        this.defaultSprint = mode;
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
            if (ov.overridesSprint() && ov.getSprint() == defaultSprint) ov.clearSprint();
            if (ov.overridesSlipperiness() && ov.getSlipperiness() == defaultSlipperiness) ov.clearSlipperiness();
            if (ov.overridesMedium() && ov.getMedium() == Medium.NONE) ov.clearMedium();
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

    /** Keep in sync with BoxStyle.HITBOX_HALF_WIDTH / AngleSolverEngine#HALF / ConstraintDeriver.HALF. */
    public static final double HITBOX_HALF_WIDTH = ConstraintDeriver.HALF;

    public void setFootprint(int tick, double xLo, double xHi, double zLo, double zHi) {
        if (tick < 0) return;
        List<Constraint> list = tickConstraints(tick).getConstraints();
        list.removeIf(c -> c.isRange() && !c.isRelative()
                && (c.getField() == Constraint.Field.X || c.getField() == Constraint.Field.Z));
        list.add(Constraint.range(Constraint.Field.X, xLo, xHi, true, true));
        list.add(Constraint.range(Constraint.Field.Z, zLo, zHi, true, true));
    }

    public void mergeFootprint(int tick, double xLo, double xHi, double zLo, double zHi) {
        if (tick < 0) return;
        List<Constraint> list = tickConstraints(tick).getConstraints();
        double finalXLo = xLo, finalXHi = xHi;
        double finalZLo = zLo, finalZHi = zHi;

        for (int i = list.size() - 1; i >= 0; i--) {
            Constraint c = list.get(i);
            if (c.isRange() && !c.isRelative() && c.getField() == Constraint.Field.X) {
                finalXLo = Math.min(finalXLo, c.getLo());
                finalXHi = Math.max(finalXHi, c.getHi());
                list.remove(i);
            } else if (c.isRange() && !c.isRelative() && c.getField() == Constraint.Field.Z) {
                finalZLo = Math.min(finalZLo, c.getLo());
                finalZHi = Math.max(finalZHi, c.getHi());
                list.remove(i);
            }
        }
        list.add(Constraint.range(Constraint.Field.X, finalXLo, finalXHi, true, true));
        list.add(Constraint.range(Constraint.Field.Z, finalZLo, finalZHi, true, true));
    }

    public void clearFootprint(int tick) {
        TickConstraints tc = ticks.get(tick);
        if (tc == null) return;
        tc.getConstraints().removeIf(c -> c.isRange() && !c.isRelative()
                && (c.getField() == Constraint.Field.X || c.getField() == Constraint.Field.Z));
    }

    public void putScalarReplacingDirection(int tick, Constraint wall) {
        if (tick < 0 || wall == null) return;
        List<Constraint> list = tickConstraints(tick).getConstraints();
        Constraint.Field field = wall.getField();
        boolean lower = isLowerBound(wall.getOp());
        list.removeIf(c -> !c.isRange() && !c.isRelative() && c.getField() == field
                && isWallOp(c.getOp()) && isLowerBound(c.getOp()) == lower);
        list.add(wall);
    }

    public void mergeWall(int tick, Constraint wall) {
        if (tick < 0 || wall == null) return;
        List<Constraint> list = tickConstraints(tick).getConstraints();
        Constraint.Field field = wall.getField();
        boolean lower = isLowerBound(wall.getOp());
        double finalVal = wall.getValue();

        for (int i = list.size() - 1; i >= 0; i--) {
            Constraint c = list.get(i);
            if (!c.isRange() && !c.isRelative() && c.getField() == field
                    && isWallOp(c.getOp()) && isLowerBound(c.getOp()) == lower) {
                if (lower) {
                    finalVal = Math.min(finalVal, c.getValue());
                } else {
                    finalVal = Math.max(finalVal, c.getValue());
                }
                list.remove(i);
            }
        }
        wall.setValue(finalVal);
        list.add(wall);
    }

    public void clearWall(int tick, Constraint.Field field, boolean lower) {
        TickConstraints tc = ticks.get(tick);
        if (tc == null) return;
        tc.getConstraints().removeIf(c -> !c.isRange() && !c.isRelative() && c.getField() == field
                && isWallOp(c.getOp()) && isLowerBound(c.getOp()) == lower);
    }

    private static boolean isWallOp(Constraint.Op op) {
        return op == Constraint.Op.GE || op == Constraint.Op.GT
                || op == Constraint.Op.LE || op == Constraint.Op.LT;
    }

    private static boolean isLowerBound(Constraint.Op op) {
        return op == Constraint.Op.GE || op == Constraint.Op.GT;
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

    public List<BlockSelection> getMomentumBlocks() {
        return momentumBlocks;
    }

    public List<BlockSelection> getCollisionBlocks() {
        return collisionBlocks;
    }

    public List<BlockSelection> getLandBlocks() {
        return landBlocks;
    }

    private List<BlockSelection> blocksFor(BlockSelection.Kind kind) {
        switch (kind) {
            case MOMENTUM: return momentumBlocks;
            case LAND: return landBlocks;
            case COLLISION:
            default: return collisionBlocks;
        }
    }

    public void addBlock(BlockSelection block) {
        if (block != null) blocksFor(block.kind).add(block);
    }

    public void toggleBlock(BlockSelection block) {
        if (block == null) return;
        boolean alreadySameRole = containsAt(blocksFor(block.kind), block.x, block.y, block.z);
        removeBlockAt(block.x, block.y, block.z);
        if (!alreadySameRole) blocksFor(block.kind).add(block);
    }

    private static boolean containsAt(List<BlockSelection> list, int x, int y, int z) {
        for (BlockSelection b : list) {
            if (b.x == x && b.y == y && b.z == z) return true;
        }
        return false;
    }

    public void removeBlockAt(int x, int y, int z) {
        momentumBlocks.removeIf(b -> b.x == x && b.y == y && b.z == z);
        collisionBlocks.removeIf(b -> b.x == x && b.y == y && b.z == z);
        landBlocks.removeIf(b -> b.x == x && b.y == y && b.z == z);
    }

    public boolean hasAnyBlocks() {
        return !momentumBlocks.isEmpty() || !collisionBlocks.isEmpty() || !landBlocks.isEmpty();
    }

    public boolean hasRequiredBlocks() {
        return !landBlocks.isEmpty();
    }

    public void clearBlocks() {
        momentumBlocks.clear();
        collisionBlocks.clear();
        landBlocks.clear();
    }

    public SolveResult getResult() {
        return result;
    }

    public void clearResult() {
        result = null;
        applyDeviation = null;
        applyDeviationKind = null;
        applyDeviationTick = -1;
    }

    public void setResult(SolveResult result) {
        this.result = result;
    }

    /** Post-apply check: where the resimmed path first left the solved path, null = matched (or no apply yet). */
    public String getApplyDeviation() {
        return applyDeviation;
    }

    public DeviationKind getApplyDeviationKind() {
        return applyDeviationKind;
    }

    public int getApplyDeviationTick() {
        return applyDeviationTick;
    }

    public void setApplyDeviation(String message, DeviationKind kind) {
        setApplyDeviation(message, kind, -1);
    }

    public void setApplyDeviation(String message, DeviationKind kind, int tick) {
        this.applyDeviation = message;
        this.applyDeviationKind = message == null ? null : kind;
        this.applyDeviationTick = message == null ? -1 : tick;
    }

    private boolean bruteForceEnabled = false;
    private int bruteForceTicks = 0;
    private int bruteForceTimeoutMs = 200;
    private int bruteForceLiveTimeoutMs = 200;
    private boolean bruteForceDynamicTimeout = false;
    private int bruteForceDynamicAddPerJumpMs = 50;
    private double bruteForceDynamicSafetyMult = 1.1;
    private int bruteForceDynamicSafetyMarginMs = 80;
    private boolean bruteForceMinTicks = false;
    private boolean bruteForceActive = false;

    public boolean isBruteForceEnabled() {
        return bruteForceEnabled;
    }

    public void setBruteForceEnabled(boolean bruteForceEnabled) {
        this.bruteForceEnabled = bruteForceEnabled;
    }

    public int getBruteForceTicks() {
        return bruteForceTicks;
    }

    public void setBruteForceTicks(int bruteForceTicks) {
        this.bruteForceTicks = Math.max(0, bruteForceTicks);
    }

    public int getBruteForceTimeoutMs() {
        return bruteForceTimeoutMs;
    }

    public void setBruteForceTimeoutMs(int bruteForceTimeoutMs) {
        this.bruteForceTimeoutMs = Math.max(1, bruteForceTimeoutMs);
        this.bruteForceLiveTimeoutMs = this.bruteForceTimeoutMs;
    }

    public int getBruteForceLiveTimeoutMs() {
        return bruteForceLiveTimeoutMs;
    }

    public void setBruteForceLiveTimeoutMs(int bruteForceLiveTimeoutMs) {
        this.bruteForceLiveTimeoutMs = bruteForceLiveTimeoutMs;
    }

    public boolean isBruteForceDynamicTimeout() {
        return bruteForceDynamicTimeout;
    }

    public void setBruteForceDynamicTimeout(boolean bruteForceDynamicTimeout) {
        this.bruteForceDynamicTimeout = bruteForceDynamicTimeout;
    }

    public int getBruteForceDynamicAddPerJumpMs() {
        return bruteForceDynamicAddPerJumpMs;
    }

    public void setBruteForceDynamicAddPerJumpMs(int bruteForceDynamicAddPerJumpMs) {
        this.bruteForceDynamicAddPerJumpMs = Math.max(0, bruteForceDynamicAddPerJumpMs);
    }

    public double getBruteForceDynamicSafetyMult() {
        return bruteForceDynamicSafetyMult;
    }

    public void setBruteForceDynamicSafetyMult(double bruteForceDynamicSafetyMult) {
        this.bruteForceDynamicSafetyMult = Math.max(1.0, bruteForceDynamicSafetyMult);
    }

    public int getBruteForceDynamicSafetyMarginMs() {
        return bruteForceDynamicSafetyMarginMs;
    }

    public void setBruteForceDynamicSafetyMarginMs(int bruteForceDynamicSafetyMarginMs) {
        this.bruteForceDynamicSafetyMarginMs = Math.max(0, bruteForceDynamicSafetyMarginMs);
    }

    public boolean isBruteForceMinTicks() {
        return bruteForceMinTicks;
    }

    public void setBruteForceMinTicks(boolean bruteForceMinTicks) {
        this.bruteForceMinTicks = bruteForceMinTicks;
    }

    public boolean isBruteForceActive() {
        return bruteForceActive;
    }

    public void setBruteForceActive(boolean bruteForceActive) {
        this.bruteForceActive = bruteForceActive;
    }

    /** Wipes all state back to construction defaults; used before loading a saved problem. */
    public void reset() {
        startTick = 0;
        landingTick = 0;
        axis = Axis.X;
        goal = Goal.MAX;
        effort = Effort.FAST;
        stopOnFeasible = false;
        legalMode = false;
        optimizeSeconds = DEFAULT_OPTIMIZE_SECONDS;
        solveBudget.resetToDefaults();
        graphPresetName = null;
        customGraph = null;
        defaultInputs = InputMode.FORCE_45;
        defaultSprint = SprintMode.ALWAYS;
        defaultSlipperiness = Slipperiness.AIR;
        defaultPotions.clear();
        ticks.clear();
        clearBlocks();
        result = null;
        applyDeviation = null;
        applyDeviationKind = null;
        applyDeviationTick = -1;
        bruteForceEnabled = false;
        bruteForceTicks = 0;
        bruteForceTimeoutMs = 200;
        bruteForceLiveTimeoutMs = 200;
        bruteForceDynamicTimeout = false;
        bruteForceDynamicAddPerJumpMs = 50;
        bruteForceDynamicSafetyMult = 1.1;
        bruteForceDynamicSafetyMarginMs = 80;
        bruteForceMinTicks = false;
        bruteForceActive = false;
    }

}
