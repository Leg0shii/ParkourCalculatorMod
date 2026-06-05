package de.legoshi.parkourcalc.core.ui.anglesolver;

import java.util.ArrayList;
import java.util.List;

/** Stubbed solve outcome rendered by the result panel (spec 6). No real solving happens yet. */
public final class SolveResult {

    /** One constraint's outcome, split into columns so the panel can align them: field, tick, the relation as written, the value the solve landed on, and the slack vs the bound (empty for ranges, where only the found value is shown). */
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

    public SolveResult(boolean success, int met, int total, int startTick, int landingTick) {
        this.success = success;
        this.met = met;
        this.total = total;
        this.startTick = startTick;
        this.landingTick = landingTick;
    }

    public boolean isSuccess() { return success; }
    public int getMet() { return met; }
    public int getTotal() { return total; }
    public int getStartTick() { return startTick; }
    public int getLandingTick() { return landingTick; }
    public List<Outcome> getOutcomes() { return outcomes; }
    public List<YawEntry> getYaws() { return yaws; }
}
