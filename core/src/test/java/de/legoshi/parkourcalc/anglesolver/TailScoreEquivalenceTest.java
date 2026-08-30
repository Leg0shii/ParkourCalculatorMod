package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class TailScoreEquivalenceTest {

    private static final String[] CAPTURES = {
            "j011-1.875x1bmdoublecross", "j016-X2jmmp2p", "j018-tds2tdsbf",
            "j013-cw2cwwinged", "j010-Xp2hsneo"
    };

    @Test
    public void tailScoreOutputMatchesFullForwardBitForBit() {
        int exercised = 0;
        int moved = 0;
        for (String cap : CAPTURES) {
            int r = checkCapture(cap);
            if (r >= 0) exercised++;
            if (r == 1) moved++;
        }
        assertTrue("no capture produced a feasible closed-form seed: test is vacuous", exercised > 0);
        assertTrue("no capture's polish moved the seed: block ascent never ran", moved > 0);
    }

    private int checkCapture(String cap) {
        ProblemFixture pf = ProblemFixture.load("solve", cap);
        ExactJumpModel model = pf.model;
        JumpSpec spec = pf.specFor(null, null);
        JumpPhysicsInputs sc = spec.asScenario();
        AtomicBoolean cancel = new AtomicBoolean(false);

        double[] raw = ClosedFormSolve.optimize(model, spec, 0.0, cancel);
        if (raw == null) return -1;
        double[] seed = Angles.wrapAll(raw);
        if (!feasible(spec, sc, model, seed)) return -1;

        long t0 = System.nanoTime();
        double[] off = BucketAscentPolish.polish(model, spec, seed, BucketAscentPolish.THOROUGH, cancel, false);
        long t1 = System.nanoTime();
        double[] on = BucketAscentPolish.polish(model, spec, seed, BucketAscentPolish.THOROUGH, cancel, true);
        long t2 = System.nanoTime();

        assertEquals(cap + ": output length changed", off.length, on.length);
        for (int k = 0; k < off.length; k++) {
            assertEquals(cap + " tick " + k + ": tail-scored yaw diverged from full-forward",
                    Double.doubleToLongBits(off[k]), Double.doubleToLongBits(on[k]));
        }

        double offMs = (t1 - t0) / 1.0e6;
        double onMs = (t2 - t1) / 1.0e6;
        System.out.printf(Locale.ROOT, "P0-TAIL-SCORE %-28s n=%-4d full=%7.1fms tail=%7.1fms speedup=%.2fx%n",
                cap, sc.numTicks, offMs, onMs, offMs / Math.max(onMs, 1.0e-9));
        return java.util.Arrays.equals(off, seed) ? 0 : 1;
    }

    private static boolean feasible(JumpSpec spec, JumpPhysicsInputs sc, ExactJumpModel model, double[] yaws) {
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = model.forward(sc, gf);
        return c.maxViolation(gf, path) <= 0.0;
    }
}
