package de.legoshi.parkourcalc.core.anglesolver.solver;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BandQpTest {

    private static final int N = 12;

    private static double[][] band(Random rnd) {
        double[][] b = new double[N][BandQp.BAND + 1];
        for (int i = 0; i < N; i++) {
            b[i][0] = 8.0 + rnd.nextDouble();
            for (int k = 1; k <= BandQp.BAND && k <= i; k++) {
                b[i][k] = rnd.nextDouble() - 0.5;
            }
        }
        return b;
    }

    private static double[] times(double[][] band, double[] v) {
        double[] out = new double[N];
        for (int i = 0; i < N; i++) {
            for (int k = 0; k <= BandQp.BAND && k <= i; k++) {
                out[i] += band[i][k] * v[i - k];
                if (k > 0) {
                    out[i - k] += band[i][k] * v[i];
                }
            }
        }
        return out;
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) {
            s += a[i] * b[i];
        }
        return s;
    }

    @Test
    public void factorSolvesTheBandedSystem() {
        Random rnd = new Random(7L);
        double[][] h = band(rnd);
        double[] want = new double[N];
        for (int i = 0; i < N; i++) {
            want[i] = rnd.nextDouble() - 0.5;
        }
        double[] got = BandQp.factor(h, 0.0).solve(times(h, want));
        for (int i = 0; i < N; i++) {
            assertEquals(want[i], got[i], 1.0e-7);
        }
    }

    @Test
    public void withoutRowsTheStepIsTheNewtonStep() {
        Random rnd = new Random(11L);
        double[][] h = band(rnd);
        double[] g = new double[N];
        for (int i = 0; i < N; i++) {
            g[i] = rnd.nextDouble() - 0.5;
        }
        double[] step = BandQp.solve(BandQp.factor(h, 0.0), g, new ArrayList<double[]>(), new ArrayList<Double>(), 1.0e7);
        double[] back = times(h, step);
        for (int i = 0; i < N; i++) {
            assertEquals(-g[i], back[i], 1.0e-7);
        }
    }

    @Test
    public void rowsHoldAndTheMultipliersAreComplementary() {
        Random rnd = new Random(23L);
        for (int trial = 0; trial < 20; trial++) {
            double[][] h = band(rnd);
            double[] g = new double[N];
            for (int i = 0; i < N; i++) {
                g[i] = 4.0 * (rnd.nextDouble() - 0.5);
            }
            List<double[]> rows = new ArrayList<double[]>();
            List<Double> rhs = new ArrayList<Double>();
            for (int r = 0; r < 5; r++) {
                double[] row = new double[N];
                for (int i = 0; i < N; i++) {
                    row[i] = rnd.nextDouble() - 0.5;
                }
                rows.add(row);
                rhs.add(0.2 * (rnd.nextDouble() - 0.3));
            }
            double[] step = BandQp.solve(BandQp.factor(h, 0.0), g, rows, rhs, 1.0e7);
            for (int r = 0; r < rows.size(); r++) {
                assertTrue("row " + r + " violated in trial " + trial, dot(rows.get(r), step) >= rhs.get(r) - 1.0e-6);
            }
            double value = 0.5 * dot(step, times(h, step)) + dot(g, step);
            for (int probe = 0; probe < 200; probe++) {
                double[] other = step.clone();
                for (int i = 0; i < N; i++) {
                    other[i] += 0.05 * (rnd.nextDouble() - 0.5);
                }
                boolean ok = true;
                for (int r = 0; r < rows.size(); r++) {
                    ok &= dot(rows.get(r), other) >= rhs.get(r);
                }
                if (ok) {
                    double otherValue = 0.5 * dot(other, times(h, other)) + dot(g, other);
                    assertTrue("a feasible neighbour is better in trial " + trial, otherValue >= value - 1.0e-6);
                }
            }
        }
    }
}
