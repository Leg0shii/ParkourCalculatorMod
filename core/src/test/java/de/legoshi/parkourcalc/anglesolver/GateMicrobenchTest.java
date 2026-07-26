package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.RazorFixtures;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GateMicrobenchTest {

    private static final double GATE_THRESHOLD = 0.005;

    @Test
    public void gatelessTwinMisSimulatesRazorProof() {
        RazorFixtures.Loaded proof = RazorFixtures.loadProofSpec();
        double[] gf = RazorFixtures.warmGameFacings(proof);

        ExactJumpModel gated = ExactJumpModel.forMcVersion(proof.file.mcVersion);
        ExactJumpModel gateless = new ExactJumpModel(0.0, true, false);
        ForwardPath gp = gated.forward(proof.scenario, gf);
        ForwardPath lp = gateless.forward(proof.scenario, gf);

        double gatedViol = JumpConstraintCompiler.compile(proof.spec).maxViolation(gf, gp);
        double gatelessViol = JumpConstraintCompiler.compile(proof.spec).maxViolation(gf, lp);

        double maxGap = 0.0;
        int maxGapTick = -1;
        for (int k = 0; k <= proof.n; k++) {
            double gap = Math.abs(gp.posX[k] - lp.posX[k]) + Math.abs(gp.posZ[k] - lp.posZ[k]);
            if (gap > maxGap) {
                maxGap = gap;
                maxGapTick = k;
            }
        }
        double finalGap = Math.abs(gp.posX[proof.n] - lp.posX[proof.n])
                + Math.abs(gp.posZ[proof.n] - lp.posZ[proof.n]);

        int xGateCount = 0;
        for (int t = 0; t < proof.n; t++) {
            if (Math.abs(gp.velX[t]) < GATE_THRESHOLD) xGateCount++;
        }

        double v5 = gp.velX[5];
        double v14 = gp.velX[14];
        double v25 = gp.velX[25];

        System.out.println("[gate-microbench] razor-proof: legacy gated (0.005) vs gateless twin new ExactJumpModel(0.0, true, false)");
        System.out.printf(Locale.ROOT, "[gate-microbench] gated viol=%.9e  gateless viol=%.9e  xGateTicks=%d%n",
                gatedViol, gatelessViol, xGateCount);
        System.out.printf(Locale.ROOT, "[gate-microbench] maxPosGap=%.9e @t%d  finalPosGap=%.9e%n",
                maxGap, maxGapTick, finalGap);
        System.out.println("[gate-microbench]  tick        gatedVelX        gatelessVelX          |posGap|");
        for (int t : new int[]{4, 5, 6, 13, 14, 15, 24, 25, 26}) {
            double gap = Math.abs(gp.posX[t] - lp.posX[t]) + Math.abs(gp.posZ[t] - lp.posZ[t]);
            System.out.printf(Locale.ROOT, "[gate-microbench] %5d  %+.12e  %+.12e  %.12e%n",
                    t, gp.velX[t], lp.velX[t], gap);
        }

        assertTrue("gated model must satisfy the proof constraints, viol=" + gatedViol, gatedViol <= 0.0);

        assertTrue("gateless max position gap must exceed 1e-6, got " + maxGap, maxGap > 1e-6);
        assertTrue("gateless final position gap must exceed 1e-6, got " + finalGap, finalGap > 1e-6);

        assertTrue("gateless twin must break at least one constraint, viol=" + gatelessViol, gatelessViol > 0.0);

        assertEquals(14, xGateCount);

        assertTrue("t5 gate must fire (|velX| < 0.005), got " + v5, Math.abs(v5) < GATE_THRESHOLD);
        assertTrue("t5 gated carry must be nonzero, got " + v5, v5 != 0.0);
        assertEquals(1.085313e-3, v5, 1e-9);

        assertTrue("t14 gate must fire (|velX| < 0.005), got " + v14, Math.abs(v14) < GATE_THRESHOLD);
        assertTrue("t14 gated carry must be nonzero, got " + v14, v14 != 0.0);
        assertEquals(4.792645e-3, v14, 1e-9);

        assertTrue("t25 gate must fire (|velX| < 0.005), got " + v25, Math.abs(v25) < GATE_THRESHOLD);
        assertTrue("t25 gated carry must be nonzero, got " + v25, v25 != 0.0);
        assertEquals(4.997855e-3, v25, 1e-9);
    }
}
