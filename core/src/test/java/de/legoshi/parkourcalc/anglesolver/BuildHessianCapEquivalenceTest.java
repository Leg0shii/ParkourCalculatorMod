package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class BuildHessianCapEquivalenceTest {

    private static final double EPS2 = 1.0e-14;

    private static final String[] CAPTURES = {
            "j021-rinav1-01", "j008b-2jump", "razor-proof", "taser-80t",
            "deserthard-planrealization", "j001"
    };

    @Test
    public void cappedHessianMatchesFull() {
        int truncatingPairs = 0;
        for (String cap : CAPTURES) {
            truncatingPairs += checkCapture(cap);
        }
        assertTrue("the cap never truncated any wall pair: test is vacuous", truncatingPairs > 0);
    }

    private int checkCapture(String cap) {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(cap));
        if (file == null) throw new IllegalStateException(cap + ": failed to parse");
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();
        JumpLinearModel lm = new JumpLinearModel(sc);
        int n = sc.numTicks;

        Objective obj = spec.objective;
        double[] cx = new double[n];
        double[] cz = new double[n];
        lm.objectiveVectors(obj, cx, cz);

        double[] wOverNrm = new double[n];
        double[] gxHat = new double[n];
        double[] gzHat = new double[n];
        for (int t = 0; t < n; t++) {
            double nrm = Math.sqrt(cx[t] * cx[t] + cz[t] * cz[t] + EPS2);
            wOverNrm[t] = lm.mMag(t) / nrm;
            gxHat[t] = cx[t] / nrm;
            gzHat[t] = cz[t] / nrm;
        }

        boolean[] trivial = new boolean[1];
        List<JumpLinearModel.Wall> walls = lm.compileWalls(spec.constraints, 0.0, trivial);
        int m = walls.size();
        int[] lastCoupled = new int[m];
        for (int j = 0; j < m; j++) {
            double[] c = walls.get(j).coef;
            int last = -1;
            int lim = Math.min(c.length, n);
            for (int t = 0; t < lim; t++) if (c[t] != 0.0) last = t;
            lastCoupled[j] = last;
        }

        int truncating = 0;
        long fullIters = 0;
        long cappedIters = 0;
        for (int a = 0; a < m; a++) {
            JumpLinearModel.Wall wi = walls.get(a);
            double[] ci = wi.coef;
            double[] hatI = wi.axis == 0 ? gxHat : gzHat;
            for (int b = a; b < m; b++) {
                JumpLinearModel.Wall wj = walls.get(b);
                double[] cj = wj.coef;
                boolean sameAxis = wi.axis == wj.axis;
                double[] hatJ = wj.axis == 0 ? gxHat : gzHat;
                int bound = Math.min(lastCoupled[a], lastCoupled[b]) + 1;
                if (bound < n) truncating++;
                fullIters += n;
                cappedIters += Math.max(bound, 0);

                double full = 0.0;
                for (int t = 0; t < n; t++) {
                    double cc = ci[t] * cj[t];
                    if (cc == 0.0) continue;
                    full += wOverNrm[t] * cc * ((sameAxis ? 1.0 : 0.0) - hatI[t] * hatJ[t]);
                }
                double capped = 0.0;
                for (int t = 0; t < bound; t++) {
                    double cc = ci[t] * cj[t];
                    if (cc == 0.0) continue;
                    capped += wOverNrm[t] * cc * ((sameAxis ? 1.0 : 0.0) - hatI[t] * hatJ[t]);
                }
                assertEquals(cap + " pair(" + a + "," + b + ") bit mismatch",
                        Double.doubleToLongBits(full), Double.doubleToLongBits(capped));
            }
        }
        double saved = fullIters == 0 ? 0.0 : 100.0 * (fullIters - cappedIters) / fullIters;
        System.out.printf(java.util.Locale.ROOT, "P0-HESSIAN-CAP %-28s n=%-4d walls=%-3d fullMACs=%-9d cappedMACs=%-9d saved=%.1f%%%n",
                cap, n, m, fullIters, cappedIters, saved);
        return truncating;
    }
}
