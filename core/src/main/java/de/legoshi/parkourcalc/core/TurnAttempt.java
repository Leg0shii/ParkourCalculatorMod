package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;

public final class TurnAttempt {

    public static final class Forecast {
        public final double[] held;
        public final double[] best;
        public final double[] bestOffset;
        public final double[] offsetLo;
        public final double[] offsetHi;
        public final int lostTick;

        public Forecast(double[] held, double[] best, double[] bestOffset, double[] offsetLo, double[] offsetHi,
                        int lostTick) {
            this.held = held;
            this.best = best;
            this.bestOffset = bestOffset;
            this.offsetLo = offsetLo;
            this.offsetHi = offsetHi;
            this.lostTick = lostTick;
        }

        double at(double[] v, int j) {
            return v == null || j < 0 || j >= v.length ? Double.NaN : v[j];
        }
    }

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
    public final float[] turnStart;
    public final float[] turnEnd;
    public final float[][] trace;
    public final boolean turnFailure;
    public final double failTurn;
    public final Forecast forecast;

    public TurnAttempt(int number, int firstTick, double[] yaws, int recorded, boolean complete, boolean landed,
                       boolean inputFailure, String verdict, double margin, int worstTick, int failTick, int failKeys,
                       int expectedKeys, int macro) {
        this(number, firstTick, yaws, recorded, complete, landed, inputFailure, verdict, margin, worstTick, failTick,
                failKeys, expectedKeys, macro, null, null, null);
    }

    public TurnAttempt(int number, int firstTick, double[] yaws, int recorded, boolean complete, boolean landed,
                       boolean inputFailure, String verdict, double margin, int worstTick, int failTick, int failKeys,
                       int expectedKeys, int macro, float[] turnStart, float[] turnEnd, float[][] trace) {
        this(number, firstTick, yaws, recorded, complete, landed, inputFailure, verdict, margin, worstTick, failTick,
                failKeys, expectedKeys, macro, turnStart, turnEnd, trace, false, Double.NaN);
    }

    public TurnAttempt(int number, int firstTick, double[] yaws, int recorded, boolean complete, boolean landed,
                       boolean inputFailure, String verdict, double margin, int worstTick, int failTick, int failKeys,
                       int expectedKeys, int macro, float[] turnStart, float[] turnEnd, float[][] trace,
                       boolean turnFailure, double failTurn) {
        this(number, firstTick, yaws, recorded, complete, landed, inputFailure, verdict, margin, worstTick, failTick,
                failKeys, expectedKeys, macro, turnStart, turnEnd, trace, turnFailure, failTurn, null);
    }

    public TurnAttempt(int number, int firstTick, double[] yaws, int recorded, boolean complete, boolean landed,
                       boolean inputFailure, String verdict, double margin, int worstTick, int failTick, int failKeys,
                       int expectedKeys, int macro, float[] turnStart, float[] turnEnd, float[][] trace,
                       boolean turnFailure, double failTurn, Forecast forecast) {
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
        this.turnStart = turnStart;
        this.turnEnd = turnEnd;
        this.trace = trace;
        this.turnFailure = turnFailure;
        this.failTurn = failTurn;
        this.forecast = forecast;
    }

    public TurnAttempt withoutTrace() {
        if (trace == null) return this;
        return new TurnAttempt(number, firstTick, yaws, recorded, complete, landed, inputFailure, verdict, margin,
                worstTick, failTick, failKeys, expectedKeys, macro, turnStart, turnEnd, null, turnFailure, failTurn,
                forecast);
    }

    public boolean hasTiming() {
        return turnStart != null && turnEnd != null;
    }

    public boolean hasForecast() {
        return forecast != null;
    }

    public int lostTick() {
        return forecast == null ? -1 : forecast.lostTick;
    }

    public float turnStartAt(int tick) {
        int j = tick - firstTick;
        return turnStart == null || j < 0 || j >= turnStart.length ? Float.NaN : turnStart[j];
    }

    public float turnEndAt(int tick) {
        int j = tick - firstTick;
        return turnEnd == null || j < 0 || j >= turnEnd.length ? Float.NaN : turnEnd[j];
    }

    public float[] traceAt(int tick) {
        int j = tick - firstTick;
        return trace == null || j < 0 || j >= trace.length ? null : trace[j];
    }

    public double heldMarginAt(int tick) {
        return forecast == null ? Double.NaN : forecast.at(forecast.held, tick - firstTick);
    }

    public double bestMarginAt(int tick) {
        return forecast == null ? Double.NaN : forecast.at(forecast.best, tick - firstTick);
    }

    public double bestOffsetAt(int tick) {
        return forecast == null ? Double.NaN : forecast.at(forecast.bestOffset, tick - firstTick);
    }

    public double offsetLoAt(int tick) {
        return forecast == null ? Double.NaN : forecast.at(forecast.offsetLo, tick - firstTick);
    }

    public double offsetHiAt(int tick) {
        return forecast == null ? Double.NaN : forecast.at(forecast.offsetHi, tick - firstTick);
    }

    public int lastForecastTick() {
        if (forecast == null || forecast.held == null) return -1;
        for (int j = Math.min(recorded, forecast.held.length) - 1; j >= 0; j--) {
            if (!Double.isNaN(forecast.held[j])) return firstTick + j;
        }
        return -1;
    }

    public boolean isMacro() {
        return macro != 0;
    }

    public boolean hasMargin() {
        return !Double.isNaN(margin);
    }

    public boolean failed() {
        return inputFailure || turnFailure;
    }

    public boolean judged() {
        return complete && !failed();
    }

    public static String signedMargin(double margin) {
        if (Double.isNaN(margin)) return "-";
        double spare = -margin;
        return (spare < 0.0 ? "-" : "+") + ConstraintText.fixedStat(Math.abs(spare));
    }
}
