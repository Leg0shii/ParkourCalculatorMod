package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.PathTranslation;

import java.util.Locale;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Translation-aware free-start scoring on the capture: for random yaw vectors the zero-width translated score
 *  byte-equals the pinned score (razor-proof), and with a sidecar {@code cap} the closed-form best translation
 *  over the [-cap, cap] box matches a brute-force grid to within three grid steps, never exceeds the pinned
 *  score, reports its own violation consistently and stays inside the box. */
final class TranslationCheck implements ProblemCheck {

    private static final int ZERO_WIDTH_DRAWS = 10;
    private static final int DRAWS = 16;
    private static final int GRID = 400;

    @Override
    public void run(ProblemFixture pf) {
        JumpSpec spec = pf.specFor(null, null);
        zeroWidth(pf.model, spec, pf.name);
        if (pf.expect.cap != null) bruteForce(pf.model, spec, pf.name, pf.expect.cap);
    }

    private static void zeroWidth(ExactJumpModel model, JumpSpec spec, String label) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        Random rng = new Random(4242L + label.hashCode());
        for (int d = 0; d < ZERO_WIDTH_DRAWS; d++) {
            double[] yawAbs = new double[n];
            for (int k = 0; k < n; k++) yawAbs[k] = rng.nextDouble() * 360.0 - 180.0;
            double[] gf = sc.toGameFacings(Angles.wrapAll(yawAbs));
            ForwardPath path = model.forward(sc, gf);
            double pinned = compiled.maxViolation(gf, path);
            PathTranslation.Trans tr = PathTranslation.bestTranslation(compiled, gf, path, 0.0, 0.0, 0.0, 0.0);
            assertEquals(label + " draw " + d + ": zero-width tx", 0.0, tr.tx, 0.0);
            assertEquals(label + " draw " + d + ": zero-width tz", 0.0, tr.tz, 0.0);
            assertEquals(label + " draw " + d + ": zero-width viol must byte-equal pinned",
                    Double.doubleToRawLongBits(pinned), Double.doubleToRawLongBits(tr.viol));
        }
    }

    private static void bruteForce(ExactJumpModel model, JumpSpec spec, String label, double cap) {
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
