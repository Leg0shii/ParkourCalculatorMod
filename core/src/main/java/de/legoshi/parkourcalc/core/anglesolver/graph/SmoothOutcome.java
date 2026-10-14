package de.legoshi.parkourcalc.core.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.solver.TrendFilterSmooth;

import java.util.Arrays;

public final class SmoothOutcome {

    public final double[] before;
    public final double[] after;
    public final TrendFilterSmooth.Stop stop;
    public final long nanos;

    public SmoothOutcome(double[] before, double[] after, TrendFilterSmooth.Stop stop, long nanos) {
        this.before = before;
        this.after = after;
        this.stop = stop;
        this.nanos = nanos;
    }

    public boolean changed() {
        return after != null && !Arrays.equals(before, after);
    }
}
