package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.anglesolver.harness.RazorFixtures;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SnapRepairPolish;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FreeStartTranslationTest {

    private static final int DRAWS = 10;

    @Test
    public void zeroWidthTranslationScoreByteEqualsPinnedOnRazorProof() {
        RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
        checkZeroWidth(l.model, l.spec, "razor-proof");
    }

    @Test
    public void zeroWidthTranslationScoreByteEqualsPinnedOnJ004() {
        ProblemFixture pf = ProblemFixture.load("closedform", "j004");
        checkZeroWidth(pf.model, pf.specFor(null, null), "j004");
    }

    @Test
    public void zeroWidthTranslationScoreByteEqualsPinnedOnJ318() {
        ProblemFixture pf = ProblemFixture.load("dualrecovery", "j318_Waza_-0_to_Block_Pane_Postwalled");
        checkZeroWidth(pf.model, pf.specFor(null, null), "j318");
    }

    private void checkZeroWidth(ExactJumpModel model, JumpSpec spec, String label) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        Random rng = new Random(4242L + label.hashCode());
        for (int d = 0; d < DRAWS; d++) {
            double[] yawAbs = new double[n];
            for (int k = 0; k < n; k++) yawAbs[k] = rng.nextDouble() * 360.0 - 180.0;
            double[] gf = sc.toGameFacings(Angles.wrapAll(yawAbs));
            ForwardPath path = model.forward(sc, gf);
            double pinned = compiled.maxViolation(gf, path);
            SnapRepairPolish.Trans tr = SnapRepairPolish.bestTranslation(compiled, gf, path, 0.0, 0.0, 0.0, 0.0);
            assertEquals(label + " draw " + d + ": zero-width tx", 0.0, tr.tx, 0.0);
            assertEquals(label + " draw " + d + ": zero-width tz", 0.0, tr.tz, 0.0);
            assertEquals(label + " draw " + d + ": zero-width viol must byte-equal pinned",
                    Double.doubleToRawLongBits(pinned), Double.doubleToRawLongBits(tr.viol));
        }
    }

    @Test
    public void freeBoxWithKnownEdgeOptimalTranslationSolvesAndAdoptsIt() {
        ProblemFixture pf = ProblemFixture.load("solve", "free-translate-edge");
        ProblemFixture.Run run = pf.solve(30_000L);
        SolveResult r = run.result;
        assertNotNull("engine returned no result", r);
        assertTrue("free-translate-edge must solve", r.isSuccess());
        JumpSpec spec = run.engine.lastSpecDebug();
        assertNotNull("engine kept no spec", spec);
        JumpPhysicsInputs sc = spec.asScenario();
        assertTrue("start box must be pinned after adoption", sc.startBox == null || sc.startBox.isPinned());
        assertEquals("Z must stay pinned at the seed", 0.5, sc.startPos.z, 1.0e-9);
        assertTrue("adopted start X " + sc.startPos.x + " must reach the +X box edge (known optimal 0.7)",
                sc.startPos.x >= 0.7 - 1.0e-6 && sc.startPos.x <= 0.7);
    }
}
