package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;

public final class TurnAttempt {

    public final int number;
    public final int firstTick;
    public final double[] yaws;
    public final int recorded;
    public final boolean complete;
    public final boolean landed;
    public final boolean inputFailure;
    public final String verdict;
    public final double margin;
    public final int worstTick;
    public final int failTick;
    public final int failKeys;
    public final int expectedKeys;
    public final int macro;

    public TurnAttempt(int number, int firstTick, double[] yaws, int recorded, boolean complete, boolean landed,
                       boolean inputFailure, String verdict, double margin, int worstTick, int failTick, int failKeys,
                       int expectedKeys, int macro) {
        this.number = number;
        this.firstTick = firstTick;
        this.yaws = yaws;
        this.recorded = recorded;
        this.complete = complete;
        this.landed = landed;
        this.inputFailure = inputFailure;
        this.verdict = verdict;
        this.margin = margin;
        this.worstTick = worstTick;
        this.failTick = failTick;
        this.failKeys = failKeys;
        this.expectedKeys = expectedKeys;
        this.macro = macro;
    }

    public boolean isMacro() {
        return macro != 0;
    }

    public boolean hasMargin() {
        return !Double.isNaN(margin);
    }

    public boolean judged() {
        return complete && !inputFailure;
    }

    public static String signedMargin(double margin) {
        if (Double.isNaN(margin)) return "-";
        double spare = -margin;
        return (spare < 0.0 ? "-" : "+") + ConstraintText.fixedStat(Math.abs(spare));
    }
}
