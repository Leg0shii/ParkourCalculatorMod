package de.legoshi.parkourcalc.core.anglesolver;

import java.util.ArrayList;
import java.util.List;

/** Solve outcome rendered by the result panel: per-constraint outcomes, found yaws, and solve stats. */
public final class SolveResult {

    /** One constraint's outcome, split into the panel's columns; the per-field examples are on the fields. */
    public static final class Outcome {
        public final String field;    // e.g. "dX"
        public final String tick;     // e.g. "T10"
        public final String relation; // e.g. "> 124.5" or "(0.1, 0.3)"
        public final String found;    // e.g. "124.920"
        public final String margin;   // e.g. "+0.420", or "" for ranges

        public Outcome(String field, String tick, String relation, String found, String margin) {
            this.field = field;
            this.tick = tick;
            this.relation = relation;
            this.found = found;
            this.margin = margin;
        }
    }

    /** One found yaw, per tick across the span. */
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

    // Solve stats (filled by the engine; defaults are harmless when a solve fails before they are set).
    private long durationMs;
    private long durationNanos;     // precise compute time of the solve itself (0 = unset / loaded result)
    private String finishedAt;     // formatted clock time when the solve finished, null if unset
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

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    /** Precise compute time of the solve itself in nanoseconds (excludes the worker-thread/poll overhead);
     *  0 when unset or for a result loaded from a save (use {@link #getDurationMs()} as the fallback). */
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
