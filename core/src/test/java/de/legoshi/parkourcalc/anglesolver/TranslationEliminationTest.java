package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.anglesolver.harness.RazorFixtures;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.PathTranslation;
import org.junit.experimental.categories.Category;
import org.junit.Test;

import java.util.Locale;
import java.util.Random;

import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class TranslationEliminationTest {

    private static final int DRAWS = 16;
    private static final int GRID = 400;

    @Test
    public void proofClosedFormMatchesBruteForce() {
        RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
        run(l.model, l.spec, "razor-proof", 0.05);
    }

    @Test
    public void j004ClosedFormMatchesBruteForce() {
        ProblemFixture pf = ProblemFixture.load("closedform", "j004");
        JumpSpec spec = pf.specFor(null, null);
        run(pf.model, spec, "j004", 0.1);
    }

    private void run(ExactJumpModel model, JumpSpec spec, String label, double cap) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double loX = -cap;
        double hiX = cap;
        double loZ = -cap;
        double hiZ = cap;
        double stepX = (hiX - loX) / (GRID - 1);
        double stepZ = (hiZ - loZ) / (GRID - 1);
        double tol = 3.0 * Math.max(stepX, stepZ);
        Random rng = new Random(9001L + label.hashCode());

        double maxGridGap = 0.0;
        double maxTrCross = 0.0;
        for (int d = 0; d < DRAWS; d++) {
            double[] yawAbs = new double[n];
            for (int k = 0; k < n; k++) yawAbs[k] = rng.nextDouble() * 360.0 - 180.0;
            double[] gf = sc.toGameFacings(Angles.wrapAll(yawAbs));
            ForwardPath path = model.forward(sc, gf);

            double pinned = translatedViol(compiled, gf, path, 0.0, 0.0);
            PathTranslation.Trans tr = PathTranslation.bestTranslation(compiled, gf, path, loX, hiX, loZ, hiZ);
            double cfViol = translatedViol(compiled, gf, path, tr.tx, tr.tz);

            double gridMin = Double.POSITIVE_INFINITY;
            for (int ix = 0; ix < GRID; ix++) {
                double tx = loX + ix * stepX;
                for (int iz = 0; iz < GRID; iz++) {
                    double tz = loZ + iz * stepZ;
                    double v = translatedViol(compiled, gf, path, tx, tz);
                    if (v < gridMin) gridMin = v;
                }
            }

            maxGridGap = Math.max(maxGridGap, cfViol - gridMin);
            maxTrCross = Math.max(maxTrCross, Math.abs(tr.viol - cfViol));

            assertTrue(label + " draw " + d + ": closed-form viol " + cfViol + " exceeds grid min " + gridMin
                    + " + tol " + tol, cfViol <= gridMin + tol);
            assertTrue(label + " draw " + d + ": translated viol " + cfViol + " exceeds pinned " + pinned,
                    cfViol <= pinned + 1.0e-9);
            assertTrue(label + " draw " + d + ": tr.viol " + tr.viol + " != recomputed " + cfViol,
                    Math.abs(tr.viol - cfViol) <= 1.0e-9);
            assertTrue(label + " draw " + d + ": shift out of box tx=" + tr.tx + " tz=" + tr.tz,
                    tr.tx >= loX - 1e-12 && tr.tx <= hiX + 1e-12 && tr.tz >= loZ - 1e-12 && tr.tz <= hiZ + 1e-12);
        }
        System.out.printf(Locale.ROOT,
                "TRANS %-12s draws=%d grid=%dx%d cap=%.4f maxGridGap=%.3e maxTrCross=%.3e (tol=%.3e)%n",
                label, DRAWS, GRID, GRID, cap, maxGridGap, maxTrCross, tol);
    }

    private static double translatedViol(JumpConstraintCompiler.Compiled compiled, double[] gf,
                                         ForwardPath path, double tx, double tz) {
        double v = 0.0;
        for (JumpConstraint c : compiled.ineq) {
            double s = JumpConstraintCompiler.translatedSlack(c, gf, path, tx, tz);
            if (s > v) v = s;
        }
        for (JumpConstraint c : compiled.eq) {
            double e = Math.abs(JumpConstraintCompiler.translatedEvaluate(c, gf, path, tx, tz));
            if (e > v) v = e;
        }
        return v;
    }
}
