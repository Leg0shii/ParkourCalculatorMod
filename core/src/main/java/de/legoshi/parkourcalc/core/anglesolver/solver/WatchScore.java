package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.Locale;

public final class WatchScore {

    public static final double VISIBLE_DEG = 0.05;
    public static final double MICRO_DEG = 2.0;
    public static final int SHORT_HOLD_TICKS = 6;
    public static final int LONG_HOLD_TICKS = 12;
    public static final double HELD_REVERSAL_COST = 0.25;
    public static final double PEAK_FLOOR_DEG = 0.5;
    public static final double PEAK_RELATIVE = 0.1;

    public static final double W_REVERSAL = 4.0;
    public static final double W_WOBBLE = 1.0;
    public static final double W_MICRO = 1.0;
    public static final double W_TURN = 0.25;
    public static final double EXCESS_PER_POINT_DEG = 90.0;
    public static final double OFF_HEADING_PER_POINT_DEG = 45.0;

    private static final double TOL = 1.0e-9;

    public final int turns;
    public final int reversals;
    public final int shakes;
    public final int shortestHoldTicks;
    public final int wobbles;
    public final int microTurns;
    public final double reversalCost;
    public final double travelDeg;
    public final double maxTurnDeg;
    public final double jerkDeg;
    public final double headingTravelDeg;
    public final double excessDeg;
    public final double offHeadingDeg;
    public final double cost;

    private WatchScore(int turns, int reversals, int shakes, int shortestHoldTicks, int wobbles, int microTurns,
                       double reversalCost, double travelDeg, double maxTurnDeg, double jerkDeg,
                       double headingTravelDeg, double excessDeg, double offHeadingDeg) {
        this.turns = turns;
        this.reversals = reversals;
        this.shakes = shakes;
        this.shortestHoldTicks = shortestHoldTicks;
        this.wobbles = wobbles;
        this.microTurns = microTurns;
        this.reversalCost = reversalCost;
        this.travelDeg = travelDeg;
        this.maxTurnDeg = maxTurnDeg;
        this.jerkDeg = jerkDeg;
        this.headingTravelDeg = headingTravelDeg;
        this.excessDeg = excessDeg;
        this.offHeadingDeg = offHeadingDeg;
        this.cost = W_REVERSAL * reversalCost + W_WOBBLE * wobbles + W_MICRO * microTurns + W_TURN * turns
                + excessDeg / EXCESS_PER_POINT_DEG + offHeadingDeg / OFF_HEADING_PER_POINT_DEG;
    }

    public static WatchScore of(double anchorYaw, double[] absWrapped, ForwardPath path) {
        double[] d = TurnShape.deltas(anchorYaw, absWrapped);
        double[] unwrapped = new double[d.length];
        double running = anchorYaw;
        for (int t = 0; t < d.length; t++) {
            running += d[t];
            unwrapped[t] = running;
        }
        HeadingBand band = path == null ? null : HeadingBand.of(path, unwrapped);
        return build(d, band, unwrapped);
    }

    public static WatchScore ofDeltas(double[] d) {
        return build(d, null, null);
    }

    private static WatchScore build(double[] d, HeadingBand band, double[] unwrapped) {
        int n = d.length;
        double travel = 0.0;
        double maxTurn = 0.0;
        double jerk = 0.0;
        for (int t = 0; t < n; t++) {
            travel += Math.abs(d[t]);
            maxTurn = Math.max(maxTurn, Math.abs(d[t]));
            if (t > 0) {
                jerk += Math.abs(d[t] - d[t - 1]);
            }
        }
        int turns = 0;
        int reversals = 0;
        int shakes = 0;
        int micro = 0;
        int shortest = Integer.MAX_VALUE;
        double reversalCost = 0.0;
        int prevSign = 0;
        int prevStart = -1;
        int start = -1;
        int sign = 0;
        int wobbles = 0;
        double mass = 0.0;
        for (int t = 0; t <= n; t++) {
            int s = 0;
            if (t < n && Math.abs(d[t]) > VISIBLE_DEG) {
                s = d[t] > 0.0 ? 1 : -1;
            }
            if (s == sign) {
                if (s != 0) {
                    mass += d[t];
                }
                continue;
            }
            if (sign != 0) {
                turns++;
                wobbles += dips(d, start, t);
                if (Math.abs(mass) < MICRO_DEG) {
                    micro++;
                }
                if (prevSign != 0 && sign != prevSign) {
                    reversals++;
                    int hold = start - prevStart;
                    shortest = Math.min(shortest, hold);
                    if (hold < SHORT_HOLD_TICKS) {
                        shakes++;
                    }
                    reversalCost += holdCost(hold);
                }
                prevSign = sign;
                prevStart = start;
            }
            sign = s;
            start = t;
            mass = s != 0 ? d[t] : 0.0;
        }
        if (shortest == Integer.MAX_VALUE) {
            shortest = 0;
        }
        double headingTravel = 0.0;
        double excess = 0.0;
        double off = 0.0;
        if (band != null) {
            headingTravel = band.headingTravelDeg();
            excess = Math.max(0.0, travel - headingTravel);
            off = band.offHeadingDeg(unwrapped);
        }
        return new WatchScore(turns, reversals, shakes, shortest, wobbles, micro, reversalCost, travel, maxTurn, jerk,
                headingTravel, excess, off);
    }

    static double holdCost(int holdTicks) {
        if (holdTicks <= SHORT_HOLD_TICKS) {
            return 1.0;
        }
        if (holdTicks >= LONG_HOLD_TICKS) {
            return HELD_REVERSAL_COST;
        }
        double share = (holdTicks - SHORT_HOLD_TICKS) / (double) (LONG_HOLD_TICKS - SHORT_HOLD_TICKS);
        return 1.0 - (1.0 - HELD_REVERSAL_COST) * share;
    }

    static int dips(double[] d, int from, int to) {
        int count = 0;
        double extreme = Math.abs(d[from]);
        int dir = 0;
        for (int t = from + 1; t < to; t++) {
            double v = Math.abs(d[t]);
            double floor = Math.max(PEAK_FLOOR_DEG, PEAK_RELATIVE * Math.max(v, extreme));
            if (dir == 0) {
                if (v - extreme > floor) {
                    dir = 1;
                    extreme = v;
                } else if (extreme - v > floor) {
                    dir = -1;
                    extreme = v;
                }
            } else if (dir > 0) {
                if (v > extreme) {
                    extreme = v;
                } else if (extreme - v > floor) {
                    dir = -1;
                    extreme = v;
                }
            } else {
                if (v < extreme) {
                    extreme = v;
                } else if (v - extreme > floor) {
                    count++;
                    dir = 1;
                    extreme = v;
                }
            }
        }
        return count;
    }

    public boolean betterThan(WatchScore other) {
        if (cost < other.cost - TOL) {
            return true;
        }
        if (cost > other.cost + TOL) {
            return false;
        }
        return travelDeg < other.travelDeg - TOL;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "cost=%.2f turns=%d reversals=%d shakes=%d wobbles=%d micro=%d travel=%.0f excess=%.0f off=%.0f max=%.0f jerk=%.0f",
                cost, turns, reversals, shakes, wobbles, microTurns, travelDeg, excessDeg, offHeadingDeg, maxTurnDeg, jerkDeg);
    }
}
