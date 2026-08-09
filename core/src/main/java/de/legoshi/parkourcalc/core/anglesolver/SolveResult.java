package de.legoshi.parkourcalc.core.anglesolver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Solve outcome rendered by the result panel: per-constraint outcomes, found yaws, and solve stats. */
public final class SolveResult {

    /** One constraint's outcome, split into the panel's columns. */
    public static final class Outcome {
        public final String field;    // e.g. "dX"
        public final String tick;     // e.g. "T10"
        public final String relation; // e.g. "> 124.5" or "(0.1, 0.3)"
        public final String found;    // e.g. "124.920"
        public final String margin;   // e.g. "+0.420" (ranges: distance to the nearest bound), "" for exact (=) targets
        public final boolean met;

        public Outcome(String field, String tick, String relation, String found, String margin) {
            this(field, tick, relation, found, margin, true);
        }

        public Outcome(String field, String tick, String relation, String found, String margin, boolean met) {
            this.field = field;
            this.tick = tick;
            this.relation = relation;
            this.found = found;
            this.margin = margin;
            this.met = met;
        }
    }

    /** One label/value line in the result panel's Details dropdown; values arrive preformatted. */
    public static final class Detail {
        public final String label;
        public final String value;

        public Detail(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    public static final class YawEntry {
        public final int tick;     // 1-based for display
        public final double yaw;

        public YawEntry(int tick, double yaw) {
            this.tick = tick;
            this.yaw = yaw;
        }
    }

    private final boolean success;
    private final int met;
    private final int total;
    private final int startTick;   // 1-based for display
    private final int landingTick; // 1-based for display
    private final List<Outcome> outcomes = new ArrayList<>();
    private final List<YawEntry> yaws = new ArrayList<>();
    private final List<Detail> details = new ArrayList<>();
    private final Set<Integer> unmetTicks = new LinkedHashSet<>();

    // Solve stats (filled by the engine; defaults are harmless when a solve fails before they are set).
    private long durationMs;
    // Compute time of the solve itself, excluding worker-thread/poll overhead;
    // 0 = unset / result loaded from a save (fall back to durationMs).
    private long durationNanos;
    private String finishedAt;     // formatted clock time when the solve finished, null if unset
    private String solver;         // algorithm that produced the yaws, null if unset / legacy save
    private double objectiveValue;
    private boolean hasObjective;

    public SolveResult(boolean success, int met, int total, int startTick, int landingTick) {
        this.success = success;
        this.met = met;
        this.total = total;
        this.startTick = startTick;
        this.landingTick = landingTick;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getMet() {
        return met;
    }

    public int getTotal() {
        return total;
    }

    public int getStartTick() {
        return startTick;
    }

    public int getLandingTick() {
        return landingTick;
    }

    public List<Outcome> getOutcomes() {
        return outcomes;
    }

    public List<YawEntry> getYaws() {
        return yaws;
    }

    public List<Detail> getDetails() {
        return details;
    }

    public Set<Integer> getUnmetTicks() {
        return unmetTicks;
    }

    public void addUnmetTick(int tick) {
        unmetTicks.add(tick);
    }

    public void addDetail(String label, String value) {
        details.add(new Detail(label, value));
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public long getDurationNanos() {
        return durationNanos;
    }

    public void setDurationNanos(long durationNanos) {
        this.durationNanos = durationNanos;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(String finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getSolver() {
        return solver;
    }

    public void setSolver(String solver) {
        this.solver = solver;
    }

    public double getObjectiveValue() {
        return objectiveValue;
    }

    public boolean hasObjective() {
        return hasObjective;
    }

    public void setObjective(double value) {
        this.objectiveValue = value;
        this.hasObjective = true;
    }
}
