package de.legoshi.parkourcalc.core.anglesolver.solver;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThrustPlanTest {

    private static final double SOFTNESS = 1.0e-3;
    private static final double EPS = 1.0e-6;

    private static double[] ones(int n) {
        double[] out = new double[n];
        Arrays.fill(out, 1.0);
        return out;
    }

    private static ThrustPlan.Row atLeastX(double[] gain, double distance) {
        double[] ax = new double[gain.length];
        for (int t = 0; t < gain.length; t++) {
            ax[t] = -gain[t];
        }
        return new ThrustPlan.Row(ax, new double[gain.length], -distance, false);
    }

    private static ThrustPlan.Row atLeastZ(double[] gain, double distance) {
        double[] az = new double[gain.length];
        for (int t = 0; t < gain.length; t++) {
            az[t] = -gain[t];
        }
        return new ThrustPlan.Row(new double[gain.length], az, -distance, false);
    }

    @Test
    public void spendsThrustOnTheTicksThatCarryFurthest() {
        List<ThrustPlan.Row> rows = new ArrayList<>();
        rows.add(atLeastX(new double[]{4.0, 3.0, 2.0, 1.0}, 5.5));
        ThrustPlan.Result r = ThrustPlan.solve(4, ones(4), rows, SOFTNESS, null, 0L);
        assertTrue(r.converged);
        assertTrue(r.violation <= ThrustPlan.TOLERANCE);
        assertEquals(1.0, r.share(0), EPS);
        assertEquals(0.5, r.share(1), EPS);
        assertEquals(0.0, r.share(2), EPS);
        assertEquals(0.0, r.share(3), EPS);
        assertTrue(r.vx[0] > 0.0);
        assertEquals(0.0, r.vz[0], EPS);
    }

    @Test
    public void wallsOnBothAxesTurnTheThrust() {
        List<ThrustPlan.Row> rows = new ArrayList<>();
        rows.add(atLeastX(new double[]{1.0}, 0.3));
        rows.add(atLeastZ(new double[]{1.0}, 0.4));
        ThrustPlan.Result r = ThrustPlan.solve(1, ones(1), rows, SOFTNESS, null, 0L);
        assertTrue(r.converged);
        assertEquals(0.3, r.vx[0], EPS);
        assertEquals(0.4, r.vz[0], EPS);
        assertEquals(0.5, r.share(0), EPS);
    }

    @Test
    public void anEqualityRowIsMetFromEitherSide() {
        double[] gain = {1.0, 1.0};
        List<ThrustPlan.Row> rows = new ArrayList<>();
        rows.add(new ThrustPlan.Row(gain.clone(), new double[2], -0.6, true));
        ThrustPlan.Result r = ThrustPlan.solve(2, ones(2), rows, SOFTNESS, null, 0L);
        assertTrue(r.converged);
        assertEquals(-0.3, r.vx[0], EPS);
        assertEquals(-0.3, r.vx[1], EPS);
        assertTrue(r.multipliers[0] > 0.0);
        rows.clear();
        rows.add(new ThrustPlan.Row(gain.clone(), new double[2], 0.6, true));
        ThrustPlan.Result other = ThrustPlan.solve(2, ones(2), rows, SOFTNESS, null, 0L);
        assertTrue(other.converged);
        assertEquals(0.3, other.vx[0], EPS);
        assertEquals(0.3, other.vx[1], EPS);
        assertTrue(other.multipliers[0] < 0.0);
    }

    @Test
    public void aSlackWallCostsNothing() {
        List<ThrustPlan.Row> rows = new ArrayList<>();
        rows.add(atLeastX(new double[]{1.0, 1.0}, -0.5));
        ThrustPlan.Result r = ThrustPlan.solve(2, ones(2), rows, SOFTNESS, null, 0L);
        assertTrue(r.converged);
        assertEquals(0, r.iterations);
        assertEquals(0.0, r.share(0), 0.0);
        assertEquals(0.0, r.share(1), 0.0);
    }

    @Test
    public void aWarmStartLandsOnTheSamePlan() {
        List<ThrustPlan.Row> rows = new ArrayList<>();
        rows.add(atLeastX(new double[]{4.0, 3.0, 2.0, 1.0}, 5.5));
        rows.add(atLeastZ(new double[]{1.0, 2.0, 3.0, 4.0}, 2.0));
        ThrustPlan.Result cold = ThrustPlan.solve(4, ones(4), rows, SOFTNESS, null, 0L);
        assertTrue(cold.converged);
        ThrustPlan.Result warm = ThrustPlan.solve(4, ones(4), rows, SOFTNESS, cold.multipliers.clone(), 0L);
        assertTrue(warm.converged);
        assertTrue(warm.iterations <= 1);
        for (int t = 0; t < 4; t++) {
            assertEquals(cold.vx[t], warm.vx[t], EPS);
            assertEquals(cold.vz[t], warm.vz[t], EPS);
        }
    }

    @Test
    public void aCappedMultiplierReportsTheWallItCannotReach() {
        List<ThrustPlan.Row> rows = new ArrayList<>();
        rows.add(atLeastX(new double[]{1.0}, 2.0));
        ThrustPlan.Result r = ThrustPlan.solve(1, ones(1), rows, SOFTNESS, null, ThrustPlan.MAX_ITERATIONS, 1000.0, 0L);
        assertEquals(1000.0, r.multipliers[0], 0.0);
        assertEquals(1.0, r.share(0), EPS);
        assertEquals(1.0, r.violation, EPS);
        assertEquals(1.0, r.residual[0], EPS);
    }

    @Test
    public void aWallNoTickCanMoveStopsTheSolve() {
        List<ThrustPlan.Row> rows = new ArrayList<>();
        rows.add(new ThrustPlan.Row(new double[2], new double[2], -1.0, false));
        ThrustPlan.Result r = ThrustPlan.solve(2, ones(2), rows, SOFTNESS, null, 0L);
        assertFalse(r.converged);
        assertEquals(0, r.iterations);
        assertEquals(1.0, r.violation, EPS);
    }
}
