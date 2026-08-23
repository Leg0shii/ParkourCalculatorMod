package de.legoshi.parkourcalc.core.anglesolver.solver;

public final class ClosestMiss {

    private double[] yaws;
    private double violation = Double.POSITIVE_INFINITY;

    public synchronized void offer(double[] absWrappedYaws, double violation) {
        if (absWrappedYaws == null || Double.isNaN(violation) || violation >= this.violation) return;
        this.yaws = absWrappedYaws.clone();
        this.violation = violation;
    }

    public synchronized boolean isEmpty() {
        return yaws == null;
    }

    public synchronized double[] yaws() {
        return yaws == null ? null : yaws.clone();
    }

    public synchronized double violation() {
        return violation;
    }
}
