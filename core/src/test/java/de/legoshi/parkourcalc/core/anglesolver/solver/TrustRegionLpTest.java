package de.legoshi.parkourcalc.core.anglesolver.solver;

import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;
import org.apache.commons.math3.optim.linear.LinearObjectiveFunction;
import org.apache.commons.math3.optim.linear.NoFeasibleSolutionException;
import org.apache.commons.math3.optim.linear.Relationship;
import org.apache.commons.math3.optim.linear.SimplexSolver;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TrustRegionLpTest {

    private static final int INSTANCES = 300;
    private static final double VALUE_TOL = 2.0e-6;
    private static final double FEAS_TOL = 1.0e-7;

    @Test
    public void matchesCommonsMathOnRandomInstances() {
        Random rng = new Random(42);
        int checkedP1 = 0;
        int checkedP2 = 0;
        int infeasibleAgreed = 0;
        int skipped = 0;
        for (int i = 0; i < INSTANCES; i++) {
            int n = 1 + rng.nextInt(30);
            int m = 1 + rng.nextInt(80);
            double tr = pick(rng, 30.0, 5.0, 0.5);
            double scale = pick(rng, 1.0, 0.01, 0.2);
            double[][] a = new double[m][n];
            double[] viol = new double[m];
            double maxViol = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < m; j++) {
                boolean zeroRow = rng.nextInt(12) == 0;
                if (!zeroRow) {
                    if (j > 0 && rng.nextInt(10) == 0) {
                        System.arraycopy(a[j - 1], 0, a[j], 0, n);
                    } else {
                        for (int v = 0; v < n; v++) {
                            a[j][v] = rng.nextInt(3) == 0 ? 0.0 : rng.nextGaussian() * scale;
                        }
                    }
                }
                viol[j] = rng.nextDouble() * 0.2 - 0.05;
                maxViol = Math.max(maxViol, viol[j]);
            }
            boolean phase1 = rng.nextBoolean();
            double sCap = Math.max(-1.0e-6, maxViol - (rng.nextInt(4) == 0 ? 0.3 : 0.0));
            double[] obj = null;
            if (!phase1) {
                obj = new double[n];
                for (int v = 0; v < n; v++) obj[v] = rng.nextGaussian() * scale;
            }

            Double refValue;
            try {
                refValue = referenceValue(a, viol, obj, tr, phase1, sCap);
            } catch (NoFeasibleSolutionException e) {
                refValue = null;
            } catch (RuntimeException e) {
                skipped++;
                continue;
            }

            TrustRegionLp.Result mine = TrustRegionLp.solve(a, viol, obj, tr, phase1, sCap, 4000);
            String tag = String.format(Locale.ROOT, "instance %d (n=%d m=%d tr=%.3g phase1=%s)", i, n, m, tr, phase1);

            if (refValue == null) {
                if (mine == null) {
                    infeasibleAgreed++;
                    continue;
                }
                double worst = worstSlack(a, viol, mine.d);
                fail(tag + ": reference infeasible but own solver returned worst=" + worst + " cap=" + sCap);
            }
            assertNotNull(tag + ": own solver returned null but reference value=" + refValue, mine);
            for (int v = 0; v < n; v++) {
                assertTrue(tag + ": d out of box " + mine.d[v],
                        Math.abs(mine.d[v]) <= tr + FEAS_TOL);
            }
            double worst = worstSlack(a, viol, mine.d);
            double myValue;
            if (phase1) {
                assertTrue(tag + ": returned s " + mine.s + " below worst slack " + worst,
                        worst <= mine.s + FEAS_TOL);
                myValue = mine.s;
                checkedP1++;
            } else {
                assertTrue(tag + ": point violates cap, worst=" + worst + " cap=" + sCap,
                        worst <= sCap + FEAS_TOL);
                myValue = 0.0;
                for (int v = 0; v < n; v++) myValue += obj[v] * mine.d[v];
                checkedP2++;
            }
            double tol = VALUE_TOL * (1.0 + Math.abs(refValue));
            assertTrue(tag + ": value " + myValue + " vs reference " + refValue,
                    Math.abs(myValue - refValue) <= tol);
        }
        System.out.printf(Locale.ROOT, "TRLP p1=%d p2=%d infeasibleAgreed=%d skipped=%d%n",
                checkedP1, checkedP2, infeasibleAgreed, skipped);
        assertTrue("too few instances checked", checkedP1 + checkedP2 >= 200);
    }

    @Test
    public void uninvolvedVariableStaysAtZero() {
        double[][] a = {{1.0, 0.0}, {-0.5, 0.0}};
        double[] viol = {0.1, 0.05};
        TrustRegionLp.Result r = TrustRegionLp.solve(a, viol, null, 30.0, true, 0.0, 1000);
        assertNotNull(r);
        assertTrue("uninvolved step " + r.d[1], r.d[1] == 0.0);
        assertTrue("worst slack " + worstSlack(a, viol, r.d), worstSlack(a, viol, r.d) <= r.s + FEAS_TOL);
    }

    private static double worstSlack(double[][] a, double[] viol, double[] d) {
        double worst = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < a.length; j++) {
            double s = viol[j];
            for (int v = 0; v < d.length; v++) s += a[j][v] * d[v];
            worst = Math.max(worst, s);
        }
        return worst;
    }

    private static Double referenceValue(double[][] a, double[] viol, double[] obj, double tr,
                                         boolean phase1, double sCap) {
        int m = a.length;
        int n = a[0].length;
        int nv = n + 1;
        List<LinearConstraint> cons = new ArrayList<>();
        for (int j = 0; j < m; j++) {
            double[] row = new double[nv];
            System.arraycopy(a[j], 0, row, 0, n);
            row[n] = -1.0;
            cons.add(new LinearConstraint(row, Relationship.LEQ, -viol[j]));
        }
        for (int v = 0; v < n; v++) {
            double[] lo = new double[nv];
            lo[v] = 1.0;
            cons.add(new LinearConstraint(lo, Relationship.LEQ, tr));
            double[] hi = new double[nv];
            hi[v] = -1.0;
            cons.add(new LinearConstraint(hi, Relationship.LEQ, tr));
        }
        double[] objRow = new double[nv];
        if (phase1) {
            objRow[n] = 1.0;
        } else {
            System.arraycopy(obj, 0, objRow, 0, n);
            double[] cap = new double[nv];
            cap[n] = 1.0;
            cons.add(new LinearConstraint(cap, Relationship.LEQ, sCap));
        }
        PointValuePair sol = new SimplexSolver().optimize(new MaxIter(20000),
                new LinearObjectiveFunction(objRow, 0.0), new LinearConstraintSet(cons),
                GoalType.MINIMIZE);
        double[] x = sol.getPoint();
        if (phase1) return x[n];
        double v = 0.0;
        for (int k = 0; k < n; k++) v += obj[k] * x[k];
        return v;
    }

    private static double pick(Random rng, double... options) {
        return options[rng.nextInt(options.length)];
    }
}
