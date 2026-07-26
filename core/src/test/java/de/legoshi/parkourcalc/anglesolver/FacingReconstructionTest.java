package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.FacingReconstruction;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FacingReconstructionTest {

    private static final int MODE_ALL_LOCKED = 0;
    private static final int MODE_ALL_UNLOCKED = 1;
    private static final int MODE_MIXED = 2;

    private static boolean[] buildMask(Random rnd, int n, int mode) {
        boolean[] mask = new boolean[n];
        for (int k = 0; k < n; k++) {
            if (mode == MODE_ALL_LOCKED) {
                mask[k] = true;
            } else if (mode == MODE_ALL_UNLOCKED) {
                mask[k] = false;
            } else {
                mask[k] = rnd.nextBoolean();
            }
        }
        return mask;
    }

    private static double[] buildFacings(Random rnd, boolean[] mask, float startYaw,
                                         boolean monotonic, float monoStep) {
        int n = mask.length;
        double[] gf = new double[n];
        float running = startYaw;
        for (int k = 0; k < n; k++) {
            if (mask[k]) {
                float locked = (float) ((rnd.nextDouble() * 2.0 - 1.0) * 179.0);
                gf[k] = locked;
                running = locked;
            } else {
                float step = monotonic ? monoStep : (float) ((rnd.nextDouble() * 2.0 - 1.0) * 30.0);
                float next = running + step;
                if (Math.abs(next) > 9900.0f) {
                    next = running - step;
                }
                running = next;
                gf[k] = running;
            }
        }
        return gf;
    }

    private static void checkRoundTrip(JumpPhysicsInputs scenario, double[] gf, int[] maxNudgeOut, int scenarioId) {
        FacingReconstruction.Result res = FacingReconstruction.reconstruct(gf, scenario);
        assertTrue("reconstruction must succeed (scenario " + scenarioId + ", failed tick " + res.failedTick + ")",
                res.ok);
        if (res.maxUlpNudge > maxNudgeOut[0]) {
            maxNudgeOut[0] = res.maxUlpNudge;
        }
        double[] round = scenario.toGameFacings(Angles.wrapAll(res.absYaws));
        assertEquals(gf.length, round.length);
        for (int k = 0; k < gf.length; k++) {
            int expected = Float.floatToIntBits((float) gf[k]);
            int actual = Float.floatToIntBits((float) round[k]);
            assertEquals("bit-exact facing mismatch at scenario " + scenarioId + " tick " + k,
                    expected, actual);
        }
    }

    @Test
    public void reconstructionRoundTripsBitExact() {
        Random rnd = new Random(0xBEEF1234L);
        int[] maxNudge = new int[1];

        for (int s = 0; s < 1000; s++) {
            int n = 20 + rnd.nextInt(41);
            int mode;
            float startYaw;
            boolean monotonic = false;
            float monoStep = 0.0f;

            if (s == 0) {
                mode = MODE_ALL_UNLOCKED;
                startYaw = 170.0f;
                monotonic = true;
                monoStep = 25.0f;
            } else if (s == 1) {
                mode = MODE_ALL_UNLOCKED;
                startYaw = -170.0f;
                monotonic = true;
                monoStep = -25.0f;
            } else {
                mode = rnd.nextInt(3);
                boolean bigStart = mode != MODE_ALL_LOCKED && rnd.nextBoolean();
                if (bigStart) {
                    startYaw = (float) ((rnd.nextDouble() * 2.0 - 1.0) * 8000.0);
                } else {
                    startYaw = (float) ((rnd.nextDouble() * 2.0 - 1.0) * 180.0);
                }
            }

            boolean[] mask = buildMask(rnd, n, mode);
            JumpPhysicsInputs scenario = new JumpPhysicsInputs(n);
            scenario.startYaw = startYaw;
            scenario.yawLockedPerTick = mask;

            double[] gf = buildFacings(rnd, mask, startYaw, monotonic, monoStep);
            checkRoundTrip(scenario, gf, maxNudge, s);
        }
        System.out.println("[reconstruction] max ULP nudge across all scenarios: " + maxNudge[0]);
    }

    private static double[] buildLargeWalk(Random rnd, int n, float startYaw, double bound, double maxStep) {
        double[] gf = new double[n];
        float running = startYaw;
        for (int k = 0; k < n; k++) {
            float step = (float) ((rnd.nextDouble() * 2.0 - 1.0) * maxStep);
            float next = running + step;
            if (Math.abs(next) > bound) next = running - step;
            running = next;
            gf[k] = running;
        }
        return gf;
    }

    @Test
    public void reconstructionLargeMagnitudeRoundTripsBitExact() {
        Random rnd = new Random(0x5A9EL);
        int[] maxNudge = new int[1];
        double maxSeen = 0.0;
        for (int s = 0; s < 500; s++) {
            int n = 40 + rnd.nextInt(21);
            float startYaw = (float) ((rnd.nextDouble() * 2.0 - 1.0) * 30.0);
            double maxStep = 60.0 + rnd.nextDouble() * 118.0;
            double[] gf = buildLargeWalk(rnd, n, startYaw, 600.0, maxStep);
            for (double v : gf) maxSeen = Math.max(maxSeen, Math.abs(v));
            boolean[] mask = new boolean[n];
            JumpPhysicsInputs scenario = new JumpPhysicsInputs(n);
            scenario.startYaw = startYaw;
            scenario.yawLockedPerTick = mask;
            checkRoundTrip(scenario, gf, maxNudge, 100000 + s);
        }
        System.out.println("[reconstruction-large] maxAbsFacing=" + maxSeen + " maxUlpNudge=" + maxNudge[0]);
    }

    @Test
    public void reconstructionWeirdpaneMagnitudeClass() {
        int n = 51;
        float startYaw = 0.0f;
        double[] gf = new double[n];
        float running = startYaw;
        Random rnd = new Random(0x217DEA1L);
        for (int k = 0; k < n; k++) {
            float step = (float) (10.7 + (rnd.nextDouble() * 2.0 - 1.0) * 9.0);
            running = running + step;
            gf[k] = running;
        }
        double maxAbs = 0.0;
        for (double v : gf) maxAbs = Math.max(maxAbs, Math.abs(v));
        assertTrue("weirdpane-class walk should reach large magnitude, got " + maxAbs, maxAbs > 500.0);

        boolean[] mask = new boolean[n];
        JumpPhysicsInputs scenario = new JumpPhysicsInputs(n);
        scenario.startYaw = startYaw;
        scenario.yawLockedPerTick = mask;

        int[] maxNudge = new int[1];
        checkRoundTrip(scenario, gf, maxNudge, 200000);
        System.out.println("[reconstruction-weirdpane] maxAbsFacing=" + maxAbs + " maxUlpNudge=" + maxNudge[0]);
    }

    @Test
    public void reconstructionRejectsUnreconstructableWrapJumps() {
        int n = 5;
        float startYaw = 10.0f;
        double[] gf = new double[n];
        gf[0] = 10.0;
        gf[1] = 30.0;
        gf[2] = 30.0 + 190.0;
        gf[3] = gf[2] + 5.0;
        gf[4] = gf[3] + 5.0;

        boolean[] mask = new boolean[n];
        JumpPhysicsInputs scenario = new JumpPhysicsInputs(n);
        scenario.startYaw = startYaw;
        scenario.yawLockedPerTick = mask;

        FacingReconstruction.Result res = FacingReconstruction.reconstruct(gf, scenario);
        assertTrue("a > 180 deg unlocked jump is genuinely unreconstructable and must be rejected", !res.ok);
        assertEquals("failure must be reported at the jump tick", 2, res.failedTick);

        boolean[] lockMask = new boolean[n];
        lockMask[2] = true;
        JumpPhysicsInputs locked = new JumpPhysicsInputs(n);
        locked.startYaw = startYaw;
        locked.yawLockedPerTick = lockMask;
        FacingReconstruction.Result lockedRes = FacingReconstruction.reconstruct(gf, locked);
        assertTrue("locking the jump tick makes the sequence reconstructable", lockedRes.ok);
    }
}
