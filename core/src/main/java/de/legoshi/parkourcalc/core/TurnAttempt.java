package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;

import java.util.Locale;

public final class TurnAttempt {

    public static final class Forecast {
        public final double[] held;
        public final double[] best;
        public final double[] bestOffset;
        public final int failedTick;
        public final double[] x;
        public final double[] z;
        public final double[] vx;
        public final double[] vz;
        public final boolean[] ground;

        public Forecast(double[] held, double[] best, double[] bestOffset, int failedTick, double[] x, double[] z,
                        double[] vx, double[] vz, boolean[] ground) {
            this.held = held;
            this.best = best;
            this.bestOffset = bestOffset;
            this.failedTick = failedTick;
            this.x = x;
            this.z = z;
            this.vx = vx;
            this.vz = vz;
            this.ground = ground;
        }

        public boolean hasMargins() {
            return held != null;
        }

        public boolean hasState() {
            return x != null && z != null && vx != null && vz != null && ground != null;
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
    public String verdict;
    public double margin;
    public double marginX = Double.NaN;
    public double marginZ = Double.NaN;
    public final int worstTick;
    public final int failTick;
    public final int failKeys;
    public final int expectedKeys;
    public final int macro;
    public final float[] turnStart;
    public final float[] turnEnd;
    public float[][] trace;
    public final boolean turnFailure;
    public final double failTurn;
    public final Forecast forecast;
    public int ordinal;
    public boolean favourite;
    public int tasFirstTick = -1;
    public volatile double[] solvedOffset;
    public int[] pressedKeys;
    public boolean[] keysFailed;
    public double[] start;

    public boolean hasKeysToUse() {
        return start != null && start.length >= 6 && pressedKeys != null && recorded > 0 && pressedKeys.length >= recorded;
    }

    public int tasTick(int tick) {
        return (tasFirstTick < 0 ? 0 : tasFirstTick) + tick - firstTick;
    }

    public boolean keysRecordedAt(int tick) {
        int j = tick - firstTick;
        if (pressedKeys == null) return j >= 0 && j < recorded;
        return j >= 0 && j < pressedKeys.length && pressedKeys[j] >= 0;
    }

    public boolean keysFailedAt(int tick) {
        int j = tick - firstTick;
        if (keysFailed == null) return inputFailure && tick == failTick;
        return j >= 0 && j < keysFailed.length && keysFailed[j];
    }

    public int pressedKeysAt(int tick) {
        int j = tick - firstTick;
        if (pressedKeys == null || j < 0 || j >= pressedKeys.length || pressedKeys[j] < 0) {
            return inputFailure && tick == failTick ? failKeys : -1;
        }
        return pressedKeys[j];
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

    public void dropTrace() {
        trace = null;
    }

    public boolean alignedTo(TurnProfileController.Current cur) {
        return tasFirstTick < 0 || cur.tasFirstTick < 0 || tasFirstTick == cur.tasFirstTick;
    }

    public double errorAt(TurnProfileController.Current cur, int tick) {
        int j = tick - firstTick;
        int k = tick - cur.startTick;
        if (!alignedTo(cur) || j < 0 || j >= recorded || k < 0 || k >= cur.n) return Double.NaN;
        return Angles.wrapDelta(yaws[j] - cur.facing[k]);
    }

    public static String turnText(double deg, double pixelDeg) {
        String text = String.format(Locale.ROOT, "%s%.2f°", deg < 0 ? "-" : "+", Math.abs(deg));
        if (!(pixelDeg > 0.0)) return text;
        return text + String.format(Locale.ROOT, " (%d px)", Math.round(Math.abs(deg) / pixelDeg));
    }

    public int missBand() {
        if (!judged() || landed || !hasMargin() || margin <= 0.0 || margin >= 0.1) return -1;
        if (margin >= 0.01) return 0;
        if (margin >= 0.001) return 1;
        if (margin >= 0.0001) return 2;
        return 3;
    }

    public boolean hasTiming() {
        return turnStart != null && turnEnd != null;
    }

    public boolean hasForecast() {
        return forecast != null && forecast.hasMargins();
    }

    public boolean hasState() {
        return forecast != null && forecast.hasState();
    }

    public boolean solved() {
        return solvedOffset != null;
    }

    public double solvedOffsetAt(int tick) {
        double[] v = solvedOffset;
        int j = tick - firstTick;
        return v == null || j < 0 || j >= v.length ? Double.NaN : v[j];
    }

    public int solvedFailedTick() {
        double[] v = solvedOffset;
        if (v == null) return -1;
        for (int j = 0; j < v.length; j++) if (!Double.isNaN(v[j]) && v[j] < 0.0) return firstTick + j;
        return -1;
    }

    public boolean solvedAnywhere() {
        double[] v = solvedOffset;
        if (v == null) return false;
        for (double d : v) if (!Double.isNaN(d)) return true;
        return false;
    }

    public int failedTick() {
        return solved() && solvedAnywhere() ? solvedFailedTick() : -1;
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

    public double bestMarginAt(int tick) {
        double off = solvedOffsetAt(tick);
        if (!Double.isNaN(off)) return -off;
        return forecast == null ? Double.NaN : forecast.at(forecast.best, tick - firstTick);
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
        return complete && !turnFailure;
    }

    public static String signedMargin(double margin) {
        if (Double.isNaN(margin)) return "-";
        double spare = -margin;
        return (spare < 0.0 ? "-" : "+") + ConstraintText.fixedStat(Math.abs(spare));
    }

    public boolean hasAxisMargins() {
        return !Double.isNaN(marginX) || !Double.isNaN(marginZ);
    }

    public void setAxisMargins(double marginX, double marginZ) {
        this.marginX = marginX;
        this.marginZ = marginZ;
    }

    public void rejudge(int axis) {
        if (!judged() || !hasAxisMargins()) return;
        margin = selectMargin(marginX, marginZ, axis);
        verdict = landingVerdict(margin, landed, worstAxis(marginX, marginZ));
    }

    public static double selectMargin(double marginX, double marginZ, int axis) {
        if (axis == TurnReference.AXIS_X && !Double.isNaN(marginX)) return marginX;
        if (axis == TurnReference.AXIS_Z && !Double.isNaN(marginZ)) return marginZ;
        if (Double.isNaN(marginX)) return marginZ;
        if (Double.isNaN(marginZ)) return marginX;
        return Math.max(marginX, marginZ);
    }

    public static String worstAxis(double marginX, double marginZ) {
        if (Double.isNaN(marginX)) return "Z";
        if (Double.isNaN(marginZ)) return "X";
        return marginX >= marginZ ? "X" : "Z";
    }

    public static String landingVerdict(double margin, boolean landed, String missAxis) {
        if (Double.isNaN(margin)) return "landing tick not reached";
        return landed ? signedMargin(margin) : signedMargin(margin) + " " + missAxis;
    }
}
