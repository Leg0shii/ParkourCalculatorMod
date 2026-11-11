package de.legoshi.parkourcalc.core.anglesolver.solver;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WatchScoreTest {

    private static WatchScore of(double... turnPerTick) {
        return WatchScore.ofDeltas(turnPerTick);
    }

    private static double[] repeat(double value, int ticks, double... then) {
        double[] d = new double[ticks + then.length];
        for (int t = 0; t < ticks; t++) {
            d[t] = value;
        }
        System.arraycopy(then, 0, d, ticks, then.length);
        return d;
    }

    private static double[] concat(double[] a, double[] b) {
        double[] d = new double[a.length + b.length];
        System.arraycopy(a, 0, d, 0, a.length);
        System.arraycopy(b, 0, d, a.length, b.length);
        return d;
    }

    @Test
    public void steadyAccelerationIsClean() {
        assertEquals(0, of(1, 4, 6, 19).wobbles);
        assertEquals(0, of(3, 3, 3, 3).wobbles);
        assertEquals(0, of(19, 6, 4, 1).wobbles);
        assertEquals(0, of(1, 4, 6, 19, 6, 4, 1).wobbles);
        assertEquals(0, of(-1, -4, -6, -19, -6, -4, -1).wobbles);
    }

    @Test
    public void accelerationThatDipsAndRisesAgainIsAWobble() {
        assertEquals(1, of(5, 3, 10, 19).wobbles);
        assertEquals(1, of(-5, -3, -10, -19).wobbles);
        assertEquals(2, of(5, 3, 10, 4, 19).wobbles);
        assertTrue(of(5, 3, 10, 19).cost > of(1, 4, 6, 19).cost);
    }

    @Test
    public void aDipBelowTheNoiseFloorIsNotAWobble() {
        assertEquals(0, of(5, 4.8, 5, 5.1).wobbles);
        assertEquals(0, of(40, 37, 40).wobbles);
    }

    @Test
    public void aReversalAfterAShortHoldIsAShake() {
        WatchScore s = of(5, 5, 5, -5, -5, -5);
        assertEquals(2, s.turns);
        assertEquals(1, s.reversals);
        assertEquals(1, s.shakes);
        assertEquals(3, s.shortestHoldTicks);
        assertEquals(1.0, s.reversalCost, 0.0);
    }

    @Test
    public void aReversalAfterALongHoldCostsLess() {
        WatchScore held = WatchScore.ofDeltas(concat(repeat(3, 12), repeat(-3, 12)));
        assertEquals(1, held.reversals);
        assertEquals(0, held.shakes);
        assertEquals(WatchScore.HELD_REVERSAL_COST, held.reversalCost, 0.0);
        WatchScore between = WatchScore.ofDeltas(concat(repeat(3, 9), repeat(-3, 9)));
        assertEquals(0, between.shakes);
        assertTrue(between.reversalCost < 1.0 && between.reversalCost > WatchScore.HELD_REVERSAL_COST);
        assertEquals(1.0, WatchScore.holdCost(WatchScore.SHORT_HOLD_TICKS), 0.0);
        assertEquals(WatchScore.HELD_REVERSAL_COST, WatchScore.holdCost(WatchScore.LONG_HOLD_TICKS), 0.0);
    }

    @Test
    public void aPauseBetweenTwoTurnsTheSameWayIsNoReversal() {
        WatchScore s = of(4, 4, 0, 0, 4, 4);
        assertEquals(2, s.turns);
        assertEquals(0, s.reversals);
        assertEquals(0, s.wobbles);
    }

    @Test
    public void microWigglesAreCounted() {
        WatchScore s = of(0.5, -0.5, 0.5, -0.5);
        assertEquals(4, s.microTurns);
        assertEquals(3, s.reversals);
        assertEquals(3, s.shakes);
        assertEquals(0, of(0.01, -0.01, 0.01).turns);
    }

    @Test
    public void oneCleanSweepBeatsTheSameTravelInWiggles() {
        WatchScore sweep = WatchScore.ofDeltas(repeat(9, 15));
        double[] wiggle = new double[15];
        for (int t = 0; t < wiggle.length; t++) {
            wiggle[t] = t % 2 == 0 ? 9 : -9;
        }
        WatchScore shaky = WatchScore.ofDeltas(wiggle);
        assertEquals(sweep.travelDeg, shaky.travelDeg, 1.0e-9);
        assertTrue(sweep.betterThan(shaky));
        assertTrue(!shaky.betterThan(sweep));
    }

    @Test
    public void spinningPastTheCourseIsExcessAndOffHeading() {
        int n = 40;
        double[] x = new double[n + 1];
        double[] y = new double[n + 1];
        double[] z = new double[n + 1];
        for (int t = 0; t <= n; t++) {
            z[t] = 0.2 * t;
        }
        ForwardPath straight = new ForwardPath(x, y, z);
        double[] ahead = new double[n];
        double[] spin = new double[n];
        for (int t = 0; t < n; t++) {
            spin[t] = Angles.wrap(9.0 * (t + 1));
        }
        WatchScore calm = WatchScore.of(0.0, ahead, straight);
        WatchScore spinning = WatchScore.of(0.0, spin, straight);
        assertEquals(0.0, calm.excessDeg, 1.0e-9);
        assertEquals(0.0, calm.offHeadingDeg, 1.0e-9);
        assertEquals(0.0, spinning.headingTravelDeg, 1.0e-9);
        assertEquals(360.0, spinning.excessDeg, 1.0e-6);
        assertTrue(spinning.offHeadingDeg > 0.0);
        assertEquals(0, spinning.reversals);
        assertTrue(calm.betterThan(spinning));
    }
}
